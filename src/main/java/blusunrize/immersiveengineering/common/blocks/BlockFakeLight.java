package blusunrize.immersiveengineering.common.blocks;

import blusunrize.immersiveengineering.common.Config.IEConfig;
import blusunrize.immersiveengineering.common.EventHandler;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.ILightValue;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.ISpawnInterdiction;
import blusunrize.immersiveengineering.common.blocks.metal.TileEntityFloodlight;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.ArrayList;

public class BlockFakeLight extends BlockIETileProvider<BlockTypes_FakeLight>
{
	public BlockFakeLight()
	{
		super("fakeLight", Material.AIR, PropertyEnum.create("type", BlockTypes_FakeLight.class), ItemBlockIEBase.class);
		setAllNotNormalBlock();
	}
	
	@Override
	public boolean isAir(IBlockState state, IBlockAccess world, BlockPos pos)
	{
		return true;
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBox(IBlockState state, World world, BlockPos pos)
	{
		return null;
	}
	@Override
	public AxisAlignedBB getSelectedBoundingBox(IBlockState state, World world, BlockPos pos)
	{
		return null;
	}

	@Override
	public ArrayList<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune)
	{
		return new ArrayList<ItemStack>();
	}
	
	@Override
	public boolean canCollideCheck(IBlockState state, boolean b)
	{
		return false;
	}
	@Override
	public boolean isCollidable()
	{
		return false;
	}
	@Override
	public RayTraceResult collisionRayTrace(IBlockState state, World par1World, BlockPos pos, Vec3d par5Vec3, Vec3d par6Vec3)
	{
		return null;
	}
	@Override
	public EnumPushReaction getMobilityFlag(IBlockState state)
	{
		return EnumPushReaction.DESTROY;
	}
	@Override
	public boolean canBeReplacedByLeaves(IBlockState state, IBlockAccess world, BlockPos pos)
	{
		return true;
	}


	@Override
	public TileEntity createNewTileEntity(World worldIn, int meta)
	{
		return new TileEntityFakeLight();
	}

	public static class TileEntityFakeLight extends TileEntityIEBase implements ITickable, ISpawnInterdiction, ILightValue
	{
		public int[] floodlightCoords = {-1,-1,-1};
		public TileEntityFakeLight()
		{
			if(IEConfig.Machines.floodlight_spawnPrevent)
				synchronized (EventHandler.interdictionTiles) {
					if (!EventHandler.interdictionTiles.contains(this))
						EventHandler.interdictionTiles.add(this);
				}
		}
		@Override
		public void update()
		{
			if(worldObj.getTotalWorldTime()%256==((getPos().getX()^getPos().getZ())&255))
			{
				if(floodlightCoords==null || floodlightCoords.length<3)
				{
					worldObj.setBlockToAir(getPos());
					return;
				}
				BlockPos floodlightPos = new BlockPos(floodlightCoords[0], floodlightCoords[1], floodlightCoords[2]);
				TileEntity tile = worldObj.getTileEntity(floodlightPos);
				if( !(tile instanceof TileEntityFloodlight) || !((TileEntityFloodlight)tile).active)
				{
					worldObj.setBlockToAir(getPos());
					return;
				}
			}

		}
		@Override
		public int getLightValue()
		{
			return 15;
		}
		@Override
		public double getInterdictionRangeSquared()
		{
			return 1024;
		}
		@Override
		public void invalidate()
		{
			synchronized (EventHandler.interdictionTiles) {
				if (EventHandler.interdictionTiles.contains(this))
					EventHandler.interdictionTiles.remove(this);
			}
			super.invalidate();
		}
		@Override
		public void readCustomNBT(NBTTagCompound nbt, boolean descPacket)
		{
			floodlightCoords = nbt.getIntArray("floodlightCoords");
		}
		@Override
		public void writeCustomNBT(NBTTagCompound nbt, boolean descPacket)
		{
			nbt.setIntArray("floodlightCoords",floodlightCoords);

		}
	}
}