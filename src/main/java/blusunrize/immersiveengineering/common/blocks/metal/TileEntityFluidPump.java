package blusunrize.immersiveengineering.common.blocks.metal;

import blusunrize.immersiveengineering.api.IEEnums.SideConfig;
import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.energy.immersiveflux.IFluxReceiver;
import blusunrize.immersiveengineering.api.fluid.IFluidPipe;
import blusunrize.immersiveengineering.common.Config.IEConfig;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IBlockBounds;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IConfigurableSides;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IHasDummyBlocks;
import blusunrize.immersiveengineering.common.blocks.TileEntityIEBase;
import blusunrize.immersiveengineering.common.blocks.metal.TileEntityFluidPipe.DirectionalFluidOutput;
import blusunrize.immersiveengineering.common.util.ChatUtils;
import blusunrize.immersiveengineering.common.util.Utils;
import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyReceiver;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;

public class TileEntityFluidPump extends TileEntityIEBase implements ITickable, IBlockBounds, IHasDummyBlocks, IConfigurableSides, IFluidPipe, IFluxReceiver,IEnergyReceiver
{
	public int[] sideConfig = new int[] {0,-1,-1,-1,-1,-1};
	public boolean dummy = false;
	public FluidTank tank = new FluidTank(4000);
	public EnergyStorage energyStorage = new EnergyStorage(8000);
	public boolean placeCobble = true;

	boolean checkingArea = false;
	Fluid searchFluid = null;
	ArrayList<BlockPos> openList = new ArrayList<BlockPos>();
	ArrayList<BlockPos> closedList = new ArrayList<BlockPos>();
	ArrayList<BlockPos> checked = new ArrayList<BlockPos>();

	@Override
	public void update()
	{
		if(dummy || worldObj.isRemote)
			return;
		if(tank.getFluidAmount()>0)
		{
			int i = outputFluid(tank.getFluid(), false);
			tank.drain(i, true);
		}

		if(worldObj.isBlockIndirectlyGettingPowered(getPos())>0||worldObj.isBlockIndirectlyGettingPowered(getPos().add(0,1,0))>0)
		{
			for(EnumFacing f : EnumFacing.values())
				if(sideConfig[f.ordinal()]==0)
				{
					TileEntity tile = worldObj.getTileEntity(getPos().offset(f));
					if(tile!=null && tile.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, f.getOpposite()))
					{
						IFluidHandler handler = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, f.getOpposite());
						FluidStack drain = handler.drain(500, false);
						if(drain == null || drain.amount <= 0)
							continue;
						int out = this.outputFluid(drain, false);
						handler.drain(out, true);
					}
					else if(worldObj.getTotalWorldTime()%20==((getPos().getX()^getPos().getZ())&19) && worldObj.getBlockState(getPos().offset(f)).getBlock()==Blocks.WATER && IEConfig.Machines.pump_infiniteWater && tank.fill(new FluidStack(FluidRegistry.WATER,1000), false)==1000 && this.energyStorage.extractEnergy(IEConfig.Machines.pump_consumption, true)>= IEConfig.Machines.pump_consumption)
					{
						int connectedSources = 0;
						for(EnumFacing f2 : EnumFacing.HORIZONTALS)
						{
							IBlockState waterState = worldObj.getBlockState(getPos().offset(f).offset(f2));
							if(waterState.getBlock()==Blocks.WATER && Blocks.WATER.getMetaFromState(waterState)==0)
								connectedSources++;
						}
						if(connectedSources>1)
						{
							this.energyStorage.extractEnergy(IEConfig.Machines.pump_consumption, false);
							this.tank.fill(new FluidStack(FluidRegistry.WATER,1000), true);
						}
					}
				}
			if(worldObj.getTotalWorldTime()%40==(((getPos().getX()^getPos().getZ()))%40+40)%40)
			{
				if(closedList.isEmpty())
					prepareAreaCheck();
				else
				{
					int target = closedList.size()-1;
					BlockPos pos = closedList.get(target);
					FluidStack fs = Utils.drainFluidBlock(worldObj, pos, false);
					if(fs==null)
						closedList.remove(target);
					else if(tank.fill(fs, false)==fs.amount && this.energyStorage.extractEnergy(IEConfig.Machines.pump_consumption, true)>= IEConfig.Machines.pump_consumption)
					{
						this.energyStorage.extractEnergy(IEConfig.Machines.pump_consumption, false);
						fs = Utils.drainFluidBlock(worldObj, pos, true);
						//						int rainbow = (closedList.size()%11)+1;
						//						if(rainbow>6)
						//							rainbow+=2;
						//						if(rainbow>9)
						//							rainbow++;
						//						worldObj.setBlock( cc.posX,cc.posY,cc.posZ, Blocks.stained_glass,rainbow, 0x3);
						if(IEConfig.Machines.pump_placeCobble && placeCobble)
							worldObj.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState());
						this.tank.fill(fs, true);
						closedList.remove(target);
					}
				}
			}
		}

		if(checkingArea)
			checkAreaTick();
	}

	public void prepareAreaCheck()
	{
		openList.clear();
		closedList.clear();
		checked.clear();
		for(EnumFacing f : EnumFacing.values())
			if(sideConfig[f.ordinal()]==0)
			{
				openList.add(getPos().offset(f));
				checkingArea = true;
			}
	}
	public void checkAreaTick()
	{
		BlockPos next = null;
		final int closedListMax = 2048;
		int timeout = 0;
		while(timeout<64 && closedList.size()<closedListMax && !openList.isEmpty())
		{
			timeout++;
			next = openList.get(0);
			if(!checked.contains(next))
			{
				Fluid fluid = Utils.getRelatedFluid(worldObj, next);
				if(fluid!=null && (fluid!=FluidRegistry.WATER||!IEConfig.Machines.pump_infiniteWater) && (searchFluid==null || fluid==searchFluid))
				{
					if(searchFluid==null)
						searchFluid = fluid;

					if (Utils.drainFluidBlock(worldObj, next, false)!=null)
						closedList.add(next);
					for(EnumFacing f : EnumFacing.values())
					{
						BlockPos pos2 = next.offset(f);
						fluid = Utils.getRelatedFluid(worldObj, pos2);
						if(!checked.contains(pos2) && !closedList.contains(pos2) && !openList.contains(pos2) && fluid!=null && (fluid!=FluidRegistry.WATER||!IEConfig.Machines.pump_infiniteWater) && (searchFluid==null || fluid==searchFluid))
							openList.add(pos2);
					}
				}
				checked.add(next);
			}
			openList.remove(0);
		}
		if(closedList.size()>=closedListMax || openList.isEmpty())
			checkingArea = false;
	}

	public int outputFluid(FluidStack fs, boolean simulate)
	{
		if(fs==null)
			return 0;

		int canAccept = fs.amount;
		if(canAccept<=0)
			return 0;

		int accelPower = IEConfig.Machines.pump_consumption_accelerate;
		final int fluidForSort = canAccept;
		int sum = 0;
		HashMap<DirectionalFluidOutput,Integer> sorting = new HashMap<DirectionalFluidOutput,Integer>();
		for(EnumFacing f : EnumFacing.values())
			if(sideConfig[f.ordinal()]==1)
			{
				TileEntity tile = worldObj.getTileEntity(getPos().offset(f));
				if(tile!=null && tile.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, f.getOpposite()))
				{
					IFluidHandler handler = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, f.getOpposite());
					FluidStack insertResource = new FluidStack(fs.getFluid(), fs.amount);
					if(tile instanceof TileEntityFluidPipe && this.energyStorage.extractEnergy(accelPower, true) >= accelPower)
					{
						insertResource.tag = new NBTTagCompound();
						insertResource.tag.setBoolean("pressurized", true);
					}
					int temp = handler.fill(insertResource, false);
					if(temp > 0)
					{
						sorting.put(new DirectionalFluidOutput(handler, tile, f), temp);
						sum += temp;
					}
				}
			}
		if(sum>0)
		{
			int f = 0;
			int i=0;
			for(DirectionalFluidOutput output : sorting.keySet())
			{
				float prio = sorting.get(output)/(float)sum;
				int amount = (int)(fluidForSort*prio);
				if(i++ == sorting.size()-1)
					amount = canAccept;
				FluidStack insertResource = new FluidStack(fs.getFluid(), amount);
				if(output.containingTile instanceof TileEntityFluidPipe && this.energyStorage.extractEnergy(accelPower,true)>=accelPower)
				{
					this.energyStorage.extractEnergy(accelPower,false);
					insertResource.tag = new NBTTagCompound();
					insertResource.tag.setBoolean("pressurized", true);
				}
				int r = output.output.fill(insertResource, !simulate);
				f += r;
				canAccept -= r;
				if(canAccept<=0)
					break;
			}
			return f;
		}
		return 0;
	}


	@Override
	public void readCustomNBT(NBTTagCompound nbt, boolean descPacket)
	{
		sideConfig = nbt.getIntArray("sideConfig");
		if(sideConfig==null || sideConfig.length!=6)
			sideConfig = new int[]{0,-1,-1,-1,-1,-1};
		dummy = nbt.getBoolean("dummy");
		if(nbt.hasKey("placeCobble"))
			placeCobble = nbt.getBoolean("placeCobble");
		tank.readFromNBT(nbt.getCompoundTag("tank"));
		energyStorage.readFromNBT(nbt);
		if(descPacket)
			this.markContainingBlockForUpdate(null);
	}

	@Override
	public void writeCustomNBT(NBTTagCompound nbt, boolean descPacket)
	{
		nbt.setIntArray("sideConfig", sideConfig);
		nbt.setBoolean("dummy", dummy);
		nbt.setBoolean("placeCobble", placeCobble);
		nbt.setTag("tank", tank.writeToNBT(new NBTTagCompound()));
		energyStorage.writeToNBT(nbt);
	}

	@Override
	public SideConfig getSideConfig(int side)
	{
		return (side>=0&&side<6)?SideConfig.values()[this.sideConfig[side]+1]: SideConfig.NONE;
	}
	@Override
	public boolean toggleSide(int side, EntityPlayer p)
	{
		if(side!=1 && !dummy)
		{
			sideConfig[side]++;
			if(sideConfig[side]>1)
				sideConfig[side]=-1;
			this.markDirty();
			this.markContainingBlockForUpdate(null);
			worldObj.addBlockEvent(getPos(), this.getBlockType(), 0, 0);
			return true;
		}
		else if (p.isSneaking())
		{
			TileEntityFluidPump master = this;
			if (dummy)
			{
				TileEntity tmp = worldObj.getTileEntity(pos.down());
				if (tmp instanceof TileEntityFluidPump)
					master = (TileEntityFluidPump) tmp;
			}
			master.placeCobble = !master.placeCobble;
			ChatUtils.sendServerNoSpamMessages(p, new TextComponentTranslation(Lib.CHAT_INFO+"pump.placeCobble."+master.placeCobble));
			return true;
		}
		return false;
	}

	SidedFluidHandler[] sidedFluidHandler = new SidedFluidHandler[6];
	@Override
	public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing)
	{
		if(capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && facing!=null && !dummy)
			return true;
		return super.hasCapability(capability, facing);
	}
	@Override
	public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing)
	{
		if(capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && facing!=null && !dummy)
		{
			if(sidedFluidHandler[facing.ordinal()]==null)
				sidedFluidHandler[facing.ordinal()] = new SidedFluidHandler(this, facing);
			return (T)sidedFluidHandler[facing.ordinal()];
		}
		return super.getCapability(capability, facing);
	}

	static class SidedFluidHandler implements IFluidHandler
	{
		TileEntityFluidPump pump;
		EnumFacing facing;
		SidedFluidHandler(TileEntityFluidPump pump, EnumFacing facing)
		{
			this.pump = pump;
			this.facing = facing;
		}

		@Override
		public int fill(FluidStack resource, boolean doFill)
		{
			if (resource == null || pump.sideConfig[facing.ordinal()]!=0)
				return 0;
			return pump.tank.fill(resource, doFill);
		}
		@Override
		public FluidStack drain(FluidStack resource, boolean doDrain)
		{
			if (resource == null)
				return null;
			return this.drain(resource.amount, doDrain);
		}
		@Override
		public FluidStack drain(int maxDrain, boolean doDrain)
		{
			if (pump.sideConfig[facing.ordinal()]!=1)
				return null;
			return pump.tank.drain(maxDrain, doDrain);
		}
		@Override
		public IFluidTankProperties[] getTankProperties()
		{
			return pump.tank.getTankProperties();
		}
	}

	@Override
	public boolean canConnectEnergy(EnumFacing from)
	{
		return from==EnumFacing.UP || (!dummy&& (from==null || this.sideConfig[from.ordinal()]==-1));
	}

	@Override
	public int receiveEnergy(EnumFacing from, int maxReceive, boolean simulate)
	{
		if(dummy)
		{
			TileEntity te = worldObj.getTileEntity(getPos().add(0,-1,0));
			if(te instanceof TileEntityFluidPump)
				return ((TileEntityFluidPump)te).receiveEnergy(from, maxReceive, simulate);
			return 0;
		}
		return energyStorage.receiveEnergy(maxReceive, simulate);
	}

	@Override
	public int getEnergyStored(EnumFacing from)
	{
		if(dummy)
		{
			TileEntity te = worldObj.getTileEntity(getPos().add(0,-1,0));
			if(te instanceof TileEntityFluidPump)
				return ((TileEntityFluidPump)te).getEnergyStored(from);
			return 0;
		}
		return energyStorage.getEnergyStored();
	}

	@Override
	public int getMaxEnergyStored(EnumFacing from)
	{
		if(dummy)
		{
			TileEntity te = worldObj.getTileEntity(getPos().add(0,-1,0));
			if(te instanceof TileEntityFluidPump)
				return ((TileEntityFluidPump)te).getMaxEnergyStored(from);
			return 0;
		}
		return energyStorage.getMaxEnergyStored();
	}


	@Override
	public boolean isDummy()
	{
		return dummy;
	}
	@Override
	public void placeDummies(BlockPos pos, IBlockState state, EnumFacing side, float hitX, float hitY, float hitZ)
	{
		worldObj.setBlockState(pos.add(0,1,0), state);
		((TileEntityFluidPump)worldObj.getTileEntity(pos.add(0,1,0))).dummy = true;
	}
	@Override
	public void breakDummies(BlockPos pos, IBlockState state)
	{
		for(int i=0; i<=1; i++)
			if(worldObj.getTileEntity(getPos().add(0,dummy?-1:0,0).add(0,i,0)) instanceof TileEntityFluidPump)
				worldObj.setBlockToAir(getPos().add(0,dummy?-1:0,0).add(0,i,0));
	}

	@Override
	public float[] getBlockBounds()
	{
		if(!dummy)
			return null;
		return new float[]{.1875f,0,.1875f, .8125f,1,.8125f};
	}

	@Override
	public boolean canOutputPressurized(boolean consumePower)
	{
		int accelPower = IEConfig.Machines.pump_consumption_accelerate;
		if(energyStorage.extractEnergy(accelPower, true)>=accelPower)
		{
			if(consumePower)
				energyStorage.extractEnergy(accelPower, false);
			return true;
		}
		return false;
	}
	@Override
	public boolean hasOutputConnection(EnumFacing side)
	{
		return side!=null&&this.sideConfig[side.ordinal()]==1;
	}
}