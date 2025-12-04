package vswe.superfactory.components.internal;


import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class StackTankHolder {
	private FluidStack    fluidStack;
	private EnumFacing    side;
	private int           sizeLeft;
	private IFluidHandler tank;

	public StackTankHolder(FluidStack fluidStack, IFluidHandler tank, EnumFacing side) {
		this.fluidStack = fluidStack;
		this.tank = tank;
		this.side = side;
		if (fluidStack != null) {
			this.sizeLeft = fluidStack.amount;
		}
	}

	public FluidStack getFluidStack() {
		return fluidStack;
	}


	public IFluidHandler getTank() {
		return tank;
	}

	public EnumFacing getSide() {
		return side;
	}

	public void reduceAmount(int val) {
		sizeLeft -= val;
		fluidStack.amount -= val;
	}

	public StackTankHolder getSplitElement(int elementAmount, int id, boolean fair) {
		StackTankHolder element   = new StackTankHolder(this.fluidStack, this.tank, this.side);
		int             oldAmount = getSizeLeft();
		int             amount    = oldAmount / elementAmount;
		if (!fair) {
			int amountLeft = oldAmount % elementAmount;
			if (id < amountLeft) {
				amount++;
			}
		}

		element.sizeLeft = amount;
		return element;
	}

	public int getSizeLeft() {
		return Math.min(fluidStack.amount, sizeLeft);
	}
}
