package vswe.superfactory.components;

public class Point {
	private int x, y;

	public Point(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public Point() {
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public Point copy() {
		return new Point(x, y);
	}

	@Override
	public int hashCode() {
		int result = x;
		result = 31 * result + y;
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		Point point = (Point) o;

		if (x != point.x)
			return false;
		return y == point.y;
	}

	public void adjustToGrid() {
		x = ((x - 2) / 10) * 10 + 2;
		y = ((y - 4) / 10) * 10 + 4;
	}
}
