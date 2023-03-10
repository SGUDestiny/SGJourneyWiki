package net.povstalec.sgjourney.block_entities.stargate;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;
import net.povstalec.sgjourney.StargateJourney;
import net.povstalec.sgjourney.init.BlockEntityInit;
import net.povstalec.sgjourney.init.PacketHandlerInit;
import net.povstalec.sgjourney.init.SoundInit;
import net.povstalec.sgjourney.packets.ClientboundUniverseStargateUpdatePacket;
import net.povstalec.sgjourney.stargate.Addressing;
import net.povstalec.sgjourney.stargate.Stargate;

public class UniverseStargateEntity extends AbstractStargateEntity
{
	public static final int WAIT_TICKS = 20;
	public int animationTicks = 0;
	
	protected static final String UNIVERSAL = StargateJourney.MODID + ":universal";
	private static final String POINT_OF_ORIGIN = UNIVERSAL;
	private static final String SYMBOLS = UNIVERSAL;

	public int oldRotation = 0;
	public int rotation = 0;
	
	public int[] addressBuffer = new int[0];
	public int symbolBuffer = 0;
	
	public UniverseStargateEntity(BlockPos pos, BlockState state) 
	{
		super(BlockEntityInit.UNIVERSE_STARGATE.get(), pos, state, Stargate.Gen.GEN_1);
	}
	
	@Override
	public void onLoad()
	{
		if(level.isClientSide)
			return;
		
		setPointOfOrigin(POINT_OF_ORIGIN);
        setSymbols(SYMBOLS);
        
        super.onLoad();
	}
	
	@Override
	public void load(CompoundTag nbt)
	{
        super.load(nbt);
        
        rotation = nbt.getInt("Rotation");
        oldRotation = rotation;
        addressBuffer = nbt.getIntArray("AddressBuffer");
        symbolBuffer = nbt.getInt("SymbolBuffer");
    }
	
	@Override
	protected void saveAdditional(@NotNull CompoundTag nbt)
	{
		super.saveAdditional(nbt);
		
		nbt.putInt("Rotation", rotation);
		nbt.putIntArray("AddressBuffer", addressBuffer);
		nbt.putInt("SymbolBuffer", symbolBuffer);
	}
	
	public SoundEvent chevronEngageSound()
	{
		return SoundInit.UNIVERSE_CHEVRON_ENGAGE.get();
	}
	
	public SoundEvent failSound()
	{
		return SoundInit.UNIVERSE_DIAL_FAIL.get();
	}
	
	public double angle()
	{
		return (double) 360 / 54;
	}
	
	public int getRotation()
	{
		return rotation;
	}
	
	public void setRotation(int rotation)
	{
		this.rotation = rotation;
	}
	
	@Override
	public void inputSymbol(int symbol)
	{
		if(level.isClientSide())
			return;
		
		if(Addressing.addressContainsSymbol(getAddress(), symbol))
			return;
		
		if(symbol > 35)
			return;
		
		if(isConnected() && symbol == 0)
			disconnectStargate();
		
		addressBuffer = Addressing.growIntArray(addressBuffer, symbol);
		PacketHandlerInit.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(this.worldPosition)), new ClientboundUniverseStargateUpdatePacket(this.worldPosition, this.symbolBuffer, this.addressBuffer, this.animationTicks, this.rotation, this.oldRotation));
	}
	
	@Override
	protected void encodeChevron(int symbol)
	{
		symbolBuffer++;
		animationTicks++;
		super.encodeChevron(symbol);
	}
	
	public int getCurrentSymbol()
	{
		int currentSymbol;
		double position = rotation / angle();
		currentSymbol = (int) position;
		if(position >= currentSymbol + 0.5)
			currentSymbol++;
		
		if(currentSymbol > 38)
			currentSymbol = currentSymbol - 39;
		
		return currentSymbol;
	}
	
	public static void tick(Level level, BlockPos pos, BlockState state, UniverseStargateEntity stargate)
	{
		if(!stargate.isConnected() && stargate.addressBuffer.length > stargate.symbolBuffer)
		{
			if(stargate.animationTicks <= 0)
				stargate.rotateStargate();
			else if(stargate.animationTicks >= WAIT_TICKS)
				stargate.animationTicks = 0;
			else if(stargate.animationTicks > 0)
				stargate.animationTicks++;
		}
		else if(!level.isClientSide())
			PacketHandlerInit.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(stargate.worldPosition)), 
					new ClientboundUniverseStargateUpdatePacket(stargate.worldPosition, stargate.symbolBuffer, stargate.addressBuffer, stargate.animationTicks, stargate.rotation, stargate.oldRotation));
		
		AbstractStargateEntity.tick(level, pos, state, (AbstractStargateEntity) stargate);
	}
	
	public void rotate(boolean clockwise)
	{
		if(clockwise)
			rotation -= 2;
		else
			rotation += 2;
		
		setChanged();
	}
	
	public boolean isCurrentSymbol(int desiredSymbol)
	{
		int whole = desiredSymbol / 4;
		int leftover = desiredSymbol % 4;
		
		double desiredPosition = 3 * (angle() / 2) + whole * 40 + (angle() * leftover);
		
		double position = (double) rotation;
		double lowerBound = (double) (desiredPosition - 1);
		double upperBound = (double) (desiredPosition + 1);

		//System.out.println(whole + " + " + leftover);
		//System.out.println(lowerBound + " < " + rotation + " < " + upperBound);
		
		if(position > lowerBound && position < upperBound)
			return true;
		
		return false;
	}
	
	public float getRotation(float partialTick)
	{
		return Mth.lerp(partialTick, this.oldRotation, this.rotation);
	}
	
	private void rotateStargate()
	{
		oldRotation = rotation;
		
		int desiredSymbol = this.addressBuffer[symbolBuffer];
		
		if(isCurrentSymbol(desiredSymbol))
		{
			if(!level.isClientSide())
				PacketHandlerInit.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(this.worldPosition)), new ClientboundUniverseStargateUpdatePacket(this.worldPosition, this.symbolBuffer, this.addressBuffer, this.animationTicks, this.rotation, this.oldRotation));
			
			if(isCurrentSymbol(0))
				this.lockPrimaryChevron();
			else
				this.encodeChevron(desiredSymbol);
			
			if(!level.isClientSide())
				PacketHandlerInit.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(this.worldPosition)), new ClientboundUniverseStargateUpdatePacket(this.worldPosition, this.symbolBuffer, this.addressBuffer, this.animationTicks, this.rotation, this.oldRotation));
		}
		else
			rotate(getBestRotationDirection(desiredSymbol));
		
		if(rotation >= 360)
		{
			rotation -= 360;
			oldRotation -= 360;
		}
		else if(rotation < 0)
		{
			rotation += 360;
			oldRotation += 360;
		}
		//String lev = level.isClientSide() ? "Client " : "Server ";
		//System.out.println(lev + " Symbols Match: " + isCurrentSymbol(desiredSymbol));
		//System.out.println((desiredSymbol + 0.1) + " > " + (rotation / angle()) + " < " + (desiredSymbol - 0.1));
	}
	
	private boolean getBestRotationDirection(int desiredSymbol)
	{
		
		int whole = desiredSymbol / 4;
		int leftover = desiredSymbol % 4;
		
		double desiredPosition = 3 * (angle() / 2) + whole * 40 + angle() * leftover;
		
		double position = (double) rotation;
		
		double difference = desiredPosition - position;
		
		if(difference >= 180.0D)
			position =+ 360.0D;
		else if(difference <= -180.0D)
			position =- 360.0D;
		
		double lowerBound = (double) (desiredPosition - 1);
		
		if(position > lowerBound)
			return true;
		else
			return false;
	}
	
	@Override
	public void resetStargate(boolean causedByFailure)
	{
		symbolBuffer = 0;
		addressBuffer = new int[0];
		super.resetStargate(causedByFailure);
	}
	
}