package vswe.superfactory;

/**
 * Created with IntelliJ IDEA.
 * User: Vswe
 * Date: 17/12/13
 * Time: 04:18
 * To change this template use File | Settings | File Templates.
 */
public class CollisionHelper {
	public static boolean disableInBoundsCheck;

	public static boolean inBounds(int leftX, int topY, int width, int height, int mX, int mY) {
		if (disableInBoundsCheck) {
			return false;
		}
		return leftX <= mX && mX <= leftX + width && topY <= mY && mY <= topY + height;
	}
}
