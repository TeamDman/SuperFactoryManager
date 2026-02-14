package vswe.superfactory.animation;

import vswe.superfactory.components.*;
import vswe.superfactory.components.internal.ComponentType;
import vswe.superfactory.tiles.TileEntityManager;

import java.util.*;

public class AnimationController {
	private static final int               MOVE_SPEED            = 300; //pixels per second
	private static final int               MOVE_SPEED_CONNECTION = 300; //pixels per second
	private static final int               MOVE_SPEED_NODE       = 250;
	private final        TileEntityManager manager;
	Map<Integer, Integer> groupNodes = new HashMap<Integer, Integer>();
	private FlowComponent       blueprint;
	private Point               blueprintNode;
	private List<FlowComponent> blueprints;
	private int                 connectionX;
	private int                 connectionY;
	private float               delay;
	private List<FlowComponent> groupTracking;
	private List<FlowComponent> items;
	private int                 menuId;
	private int                 multiplier;
	private List<Point>         nodesBlueprint;
	private Connection          nodesConnection;
	private boolean             openNext;
	private Progress            progress = Progress.GROUP;
	private boolean             setMenuInfo;
	private FlowComponent       target;
	private int                 targetConnectionX, targetConnectionY;
	private float time;
	private int   virtualId;

	public AnimationController(TileEntityManager manager, int multiplier) {
		this.manager = manager;
		this.multiplier = multiplier;
		blueprints = new ArrayList<FlowComponent>();
		Map<Integer, Integer>             ids    = new HashMap<Integer, Integer>();
		Map<Integer, List<FlowComponent>> groups = new HashMap<Integer, List<FlowComponent>>();
		for (FlowComponent c : manager.getFlowItems()) {
			FlowComponent component = c.copy();

			if (c.getParent() != null) {
				int     oldId = c.getParent().getId();
				Integer id    = ids.get(oldId);
				if (id == null) {
					if (groups.get(oldId) == null) {
						groups.put(oldId, new ArrayList<FlowComponent>());
					}
					groups.get(oldId).add(component);
					continue;
				} else {
					component.setParent(blueprints.get(id));
				}
			}

			addComponent(component, ids, groups);
		}
		for (FlowComponent item : blueprints) {
			for (int i = 0; i < item.getConnectionSet().getConnections().length; i++) {
				Connection connection = item.getConnection(i);
				if (connection != null) {
					connection.setComponentId(ids.get(connection.getComponentId()));
				}
			}
		}
		for (FlowComponent item : blueprints) {
			if (item.getParent() != null && item.getType() == ComponentType.NODE) {
				int     id    = item.getParent().getId();
				Integer count = groupNodes.get(id);
				if (count == null) {
					groupNodes.put(id, 1);
				} else {
					groupNodes.put(id, count + 1);
				}
			}
		}
		items = new ArrayList<FlowComponent>(blueprints);

		manager.getFlowItems().clear();
		manager.getZLevelRenderingList().clear();
		for (FlowComponent flowComponent : manager.getFlowItems()) {
			manager.getZLevelRenderingList().add(0, flowComponent);
		}
		manager.setSelectedComponent(null);
	}

	private void addComponent(FlowComponent component, Map<Integer, Integer> ids, Map<Integer, List<FlowComponent>> groups) {
		int oldId = component.getId();
		int newId = blueprints.size();
		ids.put(oldId, newId);
		component.setId(newId);
		blueprints.add(component);
		if (groups.get(oldId) != null) {
			for (FlowComponent o : groups.get(oldId)) {
				o.setParent(component);
				addComponent(o, ids, groups);
			}
		}
	}

	public void update(float elapsedSeconds) {
		time += elapsedSeconds * multiplier;

		while (execute())
			;
	}

	private boolean execute() {

		if (delay != 0) {
			float rem = Math.min(delay, time);
			delay -= rem;
			time -= rem;
			if (delay != 0) {
				return false;
			}
		}

		if (time <= 0) {
			time = 0;
			return false;
		}

		switch (progress) {
			case GROUP:
				if (groupTracking != null) {
					if (groupTracking.size() > 0) {
						if (!openNext) {
							for (FlowComponent component : manager.getFlowItems()) {
								component.close();
							}
							if (groupTracking.get(0) != null && groupTracking.get(0).isVisible()) {
								manager.getFlowItems().get(groupTracking.get(0).getId()).setOpen(true);
								manager.getFlowItems().get(groupTracking.get(0).getId()).setOpenMenuId(0);
								moveToFront(groupTracking.get(0));
							}

							openNext = true;
						} else {
							if (groupTracking.get(0) != null) {
								manager.getFlowItems().get(groupTracking.get(0).getId()).setOpen(false);
							}

							if (manager.getSelectedComponent() != null && manager.getSelectedComponent().isVisible(groupTracking.get(0))) {
								manager.getFlowItems().get(manager.getSelectedComponent().getId()).setOpen(true);
								manager.getFlowItems().get(manager.getSelectedComponent().getId()).setOpenMenuId(0);
								moveToFront(manager.getSelectedComponent());
							}


							manager.setSelectedComponent(groupTracking.remove(0));
							openNext = false;
						}
					} else {
						for (FlowComponent component : manager.getFlowItems()) {
							component.close();
						}
						groupTracking = null;
						progress = Progress.PLACE;
					}
					delay = 0.5F;
					return true;
				} else if (time >= 0.5F && items.size() > 0) {
					time -= 0.5F;
					blueprint = items.remove(0);
					List<FlowComponent> parents = new ArrayList<FlowComponent>();
					FlowComponent       temp    = blueprint;
					while (temp != null) {
						temp = temp.getParent();
						parents.add(temp);
					}

					FlowComponent current = manager.getSelectedComponent();
					groupTracking = new ArrayList<FlowComponent>();
					int index;
					while ((index = parents.indexOf(current)) == -1) {
						groupTracking.add(current = current.getParent());
					}


					for (int i = index - 1; i >= 0; i--) {
						groupTracking.add(parents.get(i));
					}

					if (groupTracking.size() == 0) {
						groupTracking = null;
						progress = Progress.PLACE;
					}

					return true;
				}
				break;
			case PLACE:
				manager.getFlowItems().add(target = new FlowComponent(manager, 50, 50, blueprint.getType()));
				target.setId(blueprint.getId());
				if (blueprint.getParent() != null) {
					target.setParent(manager.getFlowItems().get(blueprint.getParent().getId()));
				}
				manager.getZLevelRenderingList().add(0, target);
				virtualId = target.getId();

				progress = Progress.POSITION;
				delay = 0.25F;
				return true;
			case POSITION:
				int distanceX = blueprint.getX() - target.getX();
				int distanceY = blueprint.getY() - target.getY();
				float distance = (float) Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));

				if (distance < 1) {
					target.setX(blueprint.getX());
					target.setY(blueprint.getY());
					if (target.getType() == ComponentType.GROUP && target.getName().equals(blueprint.getName())) {
						progress = Progress.CLOSE;
					} else {
						progress = Progress.OPEN;
					}
					delay = 0.25F + distance * MOVE_SPEED;
					return true;
				}

				float timeMovement = time * MOVE_SPEED;
				float movement = Math.min(distance, timeMovement);

				target.setX(target.getX() + (int) (distanceX * (movement / distance)));
				target.setY(target.getY() + (int) (distanceY * (movement / distance)));
				time = Math.max(0, time - movement / MOVE_SPEED);
				return true;
			case OPEN:
				target.setOpen(true);
				delay = 0.15F;
				progress = Progress.RENAME;
				return true;
			case RENAME:
				if (target.getName().equals(blueprint.getName())) {
					progress = target.getType() == ComponentType.GROUP ? Progress.CLOSE : Progress.MENUS;
					menuId = target.getMenus().size() - 1;
					target.setNameEdited(false);
					delay = 0.5F;
				} else {
					if (!target.isNameBeingEdited()) {
						target.setNameEdited(true);
						delay = 0.5F;
						return true;
					}

					String name = target.getComponentName();
					if (name == null) {
						name = "";
					}
					name += blueprint.getComponentName().charAt(name.length());
					target.setComponentName(name);
					target.refreshEditing(name);
					delay = 0.1F;
				}
				return true;
			case MENUS:
				ComponentMenu menu = target.getMenus().get(menuId);
				if (menu.isVisible()) {
					if (target.getOpenMenuId() == menu.getId()) {
						if (setMenuInfo) {
							target.setOpenMenuId(-1);
						} else {
							setMenuInfo = true;
							menu.copyFrom(blueprint.getMenus().get(menu.getId()));
							delay = 0.5F;
							return true;
						}
					} else {
						setMenuInfo = false;
						target.setOpenMenuId(menu.getId());
						delay = 0.5F;
						return true;
					}
					delay = 0.5F;
				}

				if (target.getMenus().size() == 1 || menuId == target.getMenus().size() - 2) {
					progress = Progress.CLOSE;
				} else {
					menuId = (menuId + 1) % target.getMenus().size();
				}
				delay = 0.2F;
				return true;
			case CLOSE:
				target.setOpen(false);
				delay = 0.15F;
				progress = Progress.CONNECT;
				return true;
			case CONNECT:
				if (target.getType() != ComponentType.GROUP || groupNodes.get(target.getId()) == null) {
					target.setOpen(false);
					if (manager.getCurrentlyConnecting() != null) {

						distanceX = targetConnectionX - target.getOverrideX();
						distanceY = targetConnectionY - target.getOverrideY();
						distance = (float) Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));

						if (distance < 1) {
							int        connectionId = manager.getCurrentlyConnecting().getConnectionId();
							Connection connection   = blueprint.getConnection(connectionId).copy();
							target.setConnection(connectionId, connection);
							manager.getFlowItems().get(connection.getComponentId()).setConnection(connection.getConnectionId(), manager.getCurrentlyConnecting());
							manager.setCurrentlyConnecting(null);
							delay = 0.25F + distance / MOVE_SPEED_CONNECTION;
							return true;
						}

						timeMovement = time * MOVE_SPEED_CONNECTION;
						movement = Math.min(distance, timeMovement);

						target.setOverrideX(target.getOverrideX() + (int) (distanceX * (movement / distance)));
						target.setOverrideY(target.getOverrideY() + (int) (distanceY * (movement / distance)));
						time = Math.max(0, time - movement / MOVE_SPEED_CONNECTION);
					} else {
						for (int i = 0; i < blueprint.getConnectionSet().getConnections().length; i++) {
							if (target.getConnection(i) == null) {
								Connection connection = blueprint.getConnection(i);
								if (connection != null && connection.getComponentId() < virtualId && groupNodes.get(connection.getComponentId()) == null) {
									int[] location = target.getConnectionLocationFromId(i);
									if (location != null) {
										manager.setCurrentlyConnecting(new Connection(target.getId(), i));
										target.setOverrideX(location[0] + location[3] / 2);
										target.setOverrideY(location[1] + location[4] / 2);
										int[] targetConnectionLocation = manager.getFlowItems().get(connection.getComponentId()).getConnectionLocationFromId(connection.getConnectionId());
										targetConnectionX = targetConnectionLocation[0] + targetConnectionLocation[3] / 2;
										targetConnectionY = targetConnectionLocation[1] + targetConnectionLocation[4] / 2;
										return true;
									} else {
										blueprint.setConnection(i, null);
									}
								}
							}
						}

						if (target.getType() == ComponentType.NODE && target.getParent() != null) {
							int     id    = target.getParent().getId();
							Integer count = groupNodes.get(id);
							if (count == 1) {
								groupNodes.put(id, null);
								target = target.getParent();
								blueprint = blueprints.get(target.getId());
								manager.setSelectedComponent(target.getParent());
								progress = Progress.CONNECT;
								target.setOpen(true);
								target.setOpenMenuId(0);
								moveToFront(target);
								delay = 0.75F;
								return true;
							} else {
								groupNodes.put(id, count - 1);
							}
						}

						progress = Progress.NODES;
						return true;
					}
				} else {
					progress = Progress.NODES;
					return true;
				}
				break;
			case NODES:
				if (nodesConnection != null) {
					if (blueprintNode == null) {
						if (nodesBlueprint.size() == 0) {
							nodesConnection = null;
							return true;
						} else {
							blueprintNode = nodesBlueprint.remove(0);
							if (nodesConnection.getNodes().size() == 0) {
								nodesConnection.addAndSelectNode(connectionX, connectionY, 0);
							} else {
								nodesConnection.addAndSelectNode(nodesConnection.getNodes().get(0).getX(), nodesConnection.getNodes().get(0).getY(), 0);
							}

							delay = 0.2F;
							return true;
						}
					} else {
						Point node = nodesConnection.getSelectedNode();


						distanceX = blueprintNode.getX() - node.getX();
						distanceY = blueprintNode.getY() - node.getY();
						distance = (float) Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));

						if (distance < 1) {
							node.setX(blueprintNode.getX());
							node.setY(blueprintNode.getY());
							nodesConnection.setSelectedNode(null);
							blueprintNode = null;
							delay = distance / MOVE_SPEED_NODE;
							return true;
						}

						timeMovement = time * MOVE_SPEED_NODE;
						movement = Math.min(distance, timeMovement);

						node.setX(node.getX() + (int) (distanceX * (movement / distance)));
						node.setY(node.getY() + (int) (distanceY * (movement / distance)));
						time = Math.max(0, time - movement / MOVE_SPEED_NODE);
					}
				} else {
					for (int i = 0; i < target.getConnectionSet().getConnections().length; i++) {
						if (target.getConnection(i) != null) {
							boolean       reverse          = target.getId() >= target.getConnection(i).getComponentId();
							FlowComponent other            = blueprints.get(target.getConnection(i).getComponentId());
							FlowComponent otherTarget      = manager.getFlowItems().get(target.getConnection(i).getComponentId());
							Connection    connection       = reverse ? other.getConnection(target.getConnection(i).getConnectionId()) : blueprint.getConnection(i);
							Connection    targetConnection = reverse ? otherTarget.getConnection(target.getConnection(i).getConnectionId()) : target.getConnection(i);
							if (targetConnection.getNodes().isEmpty() && !connection.getNodes().isEmpty()) {


								nodesConnection = targetConnection;
								nodesBlueprint = new ArrayList<Point>(connection.getNodes());
								System.out.println(nodesBlueprint.size());
								if (reverse) {
									Collections.reverse(nodesBlueprint);
								}

								int[] location = reverse ? other.getConnectionLocationFromId(target.getConnection(i).getConnectionId()) : target.getConnectionLocationFromId(i);

								connectionX = location[0] + location[3] / 2;
								connectionY = location[1] + location[4] / 2;

								return true;
							}
						}
					}

					progress = Progress.GROUP;
					return true;
				}

		}

		return false;
	}

	private void moveToFront(FlowComponent c) {
		FlowComponent component = manager.getFlowItems().get(c.getId());
		manager.getZLevelRenderingList().remove(c);
		manager.getZLevelRenderingList().add(0, component);
	}


	private enum Progress {
		GROUP,
		PLACE,
		POSITION,
		OPEN,
		RENAME,
		MENUS,
		CLOSE,
		CONNECT,
		NODES
	}

}