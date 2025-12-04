package vswe.superfactory.util;

import java.util.UUID;

public class UserPermission {
	private boolean active;
	private String  name;
	private boolean op;
	private UUID    userId;

	public UserPermission(UUID userId, String name) {
		this.userId = userId;
		if (name == null) {
			this.name = "Unknown";
		} else {
			this.name = name;
		}
	}

	public UserPermission copy() {
		UserPermission temp = new UserPermission(getUserId(), getUserName());
		temp.setOp(isOp());
		temp.setActive(isActive());
		return temp;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getUserName() {
		return name;
	}

	public boolean isOp() {
		return op;
	}

	public void setOp(boolean op) {
		this.op = op;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}
}
