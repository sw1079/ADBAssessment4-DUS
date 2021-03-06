package fvs.taxe.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.SnapshotArray;

import fvs.taxe.SoundPlayer;
import fvs.taxe.TaxeGame;
import fvs.taxe.Tooltip;
import fvs.taxe.actor.CollisionStationActor;
import fvs.taxe.actor.ConnectionActor;
import fvs.taxe.actor.StationActor;
import fvs.taxe.clickListener.StationClickListener;
import fvs.taxe.clickListener.TrainClicked;
import fvs.taxe.dialog.DialogStationMultitrain;
import gameLogic.Game;
import gameLogic.GameState;
import gameLogic.goal.Goal;
import gameLogic.listeners.ConnectionChangedListener;
import gameLogic.listeners.DayChangedListener;
import gameLogic.listeners.StationChangedListener;
import gameLogic.map.CollisionStation;
import gameLogic.map.Connection;
import gameLogic.map.IPositionable;
import gameLogic.map.Station;
import gameLogic.player.Player;
import gameLogic.resource.Resource;
import gameLogic.resource.Train;

/**Controller for the Graphical interface of stations*/
public class StationController {
	
	/**The Width of a connection between stations, in pixels.*/
	public final static int CONNECTION_LINE_WIDTH = 5;

	/**The context of the game.*/
	private static Context context;
	
	/**The ToolTip to be used to display Station information.*/
	private static Tooltip tooltip;

	/** The group of actors that correspond to the station's actors */
	private static Group stationActors;
	
	/** The group of actors that correspond to the connection's actors */
	private Group connectionActors;
	
	/*
	have to use CopyOnWriteArrayList because when we iterate through our listeners and execute
	their handler's method, one case unsubscribes from the event removing itself from this list
	and this list implementation supports removing elements whilst iterating through it
	 */
	/**The collection of station click listeners that is populated externally using subscribeStationClick().*/
	private static List<StationClickListener> stationClickListeners = new CopyOnWriteArrayList<StationClickListener>();

	/**Instatiation method.
	 * @param context The Context of the game.
	 * @param tooltip The tooltip used to display Station information.
	 */
	public StationController(final Context context, Tooltip tooltip) {
		StationController.context = context;
		StationController.tooltip = tooltip;

		// remove and add connections accordingly
		ConnectionController.subscribeConnectionChanged(new ConnectionChangedListener() {
			@Override
			public void removed(Connection connection) {
				connectionActors.removeActor(connection.getActor());
			}

			@Override
			public void added(Connection connection) {
				final IPositionable start = connection.getStation1().getPosition();
				final IPositionable end = connection.getStation2().getPosition();
				ConnectionActor connectionActor = new ConnectionActor(Color.GRAY, start, end, CONNECTION_LINE_WIDTH);
				connection.setActor(connectionActor);
				connectionActors.addActor(connectionActor);
			}
		});

		// remove and add stations accordingly
		ConnectionController.subscribeStationAdded(new StationChangedListener() {
			@Override
			public void stationAdded(Station station) {
				stationActors.addActor(station.getActor());
			}

			@Override
			public void stationRemoved(Station station) {
				if (station.getClass().equals(CollisionStation.class)){
					stationActors.removeActor(((CollisionStation) station).getCollisionStationActor());
				}
			}
		});

		// change the images of the stations when day changed
		context.getGameLogic().getPlayerManager().subscribeDayChanged(new DayChangedListener() {
			@Override
			public void changed(Boolean isNight) {
				SnapshotArray<Actor> list = stationActors.getChildren();
				for (Actor actor : list){
					if (actor.getClass().equals(StationActor.class)){
						((StationActor) actor).setNight(isNight);
					} else {
						((CollisionStationActor) actor).setNight(isNight);
					}
				}
			}
		});
	}

	public static void subscribeStationClick(StationClickListener listener) {
		stationClickListeners.add(listener);
	}

	public static void unsubscribeStationClick(StationClickListener listener) {
		stationClickListeners.remove(listener);
	}

	/**When a station is clicked this method is called. The controller then passes this click notification to all of the stationClickListeners.*/
	private static void stationClicked(Station station) {
		for (StationClickListener listener : stationClickListeners) {
			listener.clicked(station);
		}
	}

	/**This method draws all of the stations, as Stations or CollisionStations.*/
	public void renderStations() {
		//Calls the relevant rendering methods from within the controller class based on what type of station needs to be rendered
		List<Station> stations = context.getGameLogic().getMap().getStations();

		stationActors = new Group();
		//Iterates through every station and renders them on the GUI
		for (Station station : stations) {
			if (station instanceof CollisionStation) {
				//stationActors.addActor(renderCollisionStation(station));
				renderCollisionStation((CollisionStation) station);
			} else {
				//stationActors.addActor(renderStation(station));
				renderStation(station);
			}
		}
		renderStationGoalHighlights();
		context.getStage().addActor(stationActors);
	}

	/**This method creates a StationActor from the station and adds Enter and Exit methods to it.
	 * @param station The Station to used to create the StationActor.
	 */
	public static StationActor renderStation(final Station station) {
		//This method renders the station passed to the method
		final StationActor stationActor = new StationActor(station.getPosition(), station);

		//Creates new click listener for that station
		stationActor.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				SoundPlayer.playSound(5);
				//This routine finds all trains located at this station by iterating through every one and checking if the location equals the station location
				if (Game.getInstance().getState() == GameState.NORMAL) {
					ArrayList<Train> trains = new ArrayList<Train>();
					for (Player player : context.getGameLogic().getPlayerManager()
							.getAllPlayers()) {
						for (Resource resource : player.getResources()) {
							if (resource instanceof Train) {
								if (((Train) resource).getPosition() == station.getPosition()) {
									trains.add((Train) resource);
								}
							}
						}
					}
					if (trains.size() == 1) {
						//If there is only one train here it immediately simulates the train click
						TrainClicked clicker = new TrainClicked(context, trains.get(0));
						clicker.clicked(null, -1, 0);
					} else if (trains.size() > 1) {
						//If there is more than one of a particular train then the multitrain dialog is called using the list of trains
						DialogStationMultitrain dia = new DialogStationMultitrain(trains,
								context.getSkin(), context);
						dia.show(context.getStage());
					}
				}
				stationClicked(station);
			}

			@Override
			public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
				//When the mouse enters the station the tooltip is generated and shown for that station
				tooltip.setPosition(stationActor.getX() + 20, stationActor.getY() + 20);
				tooltip.show(station.getName());
			}

			@Override
			public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
				//When the mouse exits the station the tooltip is hidden
				tooltip.hide();
			}
		});

		station.setActor(stationActor);
		stationActors.addActor(stationActor);
		context.getGameLogic().getPlayerManager();
		stationActor.setNight(context.getGameLogic().getPlayerManager().isNight());
		return stationActor;
	}

	/**This method creates a StationActor from the CollisionStation and adds Clicked, Enter and Exit methods to it.
	 * @param collisionStation The CollisionStation to used to create the StationActor.
	 */
	public static CollisionStationActor renderCollisionStation(final CollisionStation collisionStation) {
		//Carries out the same code but this time as a collision station
		final CollisionStationActor collisionStationActor = new CollisionStationActor(
				collisionStation.getPosition(), collisionStation);

		//No need for a thorough clicked routine in the collision station unlike the standard station as trains cannot be located on a collision station
		collisionStationActor.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				if (Game.getInstance().getState() == GameState.NORMAL) {
					ArrayList<Train> trains = new ArrayList<Train>();
					for (Player player : context.getGameLogic().getPlayerManager().getAllPlayers()) {
						for (Resource resource : player.getResources()) {
							if (resource instanceof Train) {
								if (((Train) resource).getPosition() == collisionStation.getPosition()) {
									trains.add((Train) resource);
								}
							}
						}
					}
					if (trains.size() == 1) {
						//If there is only one train here it immediately simulates the train click
						TrainClicked clicker = new TrainClicked(context, trains.get(0));
						clicker.clicked(null, -1, 0);
					} else if (trains.size() > 1) {
						//If there is more than one of a particular train then the multitrain dialog is called using the list of trains
						DialogStationMultitrain dia = new DialogStationMultitrain(trains,
								context.getSkin(), context);
						dia.show(context.getStage());
					}
				}
				stationClicked(collisionStation);
			}

			@Override
			public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
				//Shows tooltip indicating the station's name
				tooltip.setPosition(collisionStationActor.getX() + 10,
						collisionStationActor.getY() + 10);
				tooltip.show("Junction");
			}

			@Override
			public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
				//Hides tooltip when the mouse leaves the station
				tooltip.hide();
			}
		});
		collisionStation.setActor(collisionStationActor);
		stationActors.addActor(collisionStationActor);
		context.getGameLogic().getPlayerManager();
		collisionStationActor.setNight(context.getGameLogic().getPlayerManager().isNight());
		return collisionStationActor;
	}

	/** The array of the colours for the station highlights */
	public static Color[] colours = {Color.RED, Color.GREEN, Color.PINK};

	/** This method is responsible for rendering the colours around the goal nodes when placing a train or routing */
	public void renderStationGoalHighlights() {
		List<Station> stations = context.getGameLogic().getMap().getStations();
		ArrayList<StationHighlight> list = new ArrayList<StationHighlight>();
		for (Station station : stations) {
			if (Game.getInstance().getState() == GameState.PLACING_TRAIN ||
					Game.getInstance().getState() == GameState.ROUTING) {
				int index = 0;
				//Creates a new hashmap which stores the maximum radius of each station
				HashMap<String, Integer> map = new HashMap<String, Integer>();
				for (Goal goal : Game.getInstance().getPlayerManager().getCurrentPlayer()
						.getGoals()) {
					if (!goal.getComplete()) {
						if (goal.getOrigin().equals(station) ||
								goal.getDestination().equals(station) ||
								goal.getIntermediary().equals(station)) {
							//If the station matches a node in the goal then the radius is calculated
							int radius;
							if (map.containsKey(station.getName())) {
								//If the station already has a value in the hashmap then the value for this coloured circle is set to that value + 5
								radius = map.get(station.getName()) + 5;
							} else {
								//Otherwise the radius is set to 15
								radius = 15;
							}
							//The value of the radius in the hashmap is updated
							map.put(station.getName(), radius);

							//The StationHighlight is added to the list to be drawn later
							list.add(new StationHighlight(station, radius, colours[index]));
						}
						index++;
					}
				}
			}
		}
		Collections.sort(list);
		Collections.reverse(list);
		TaxeGame game = context.getTaxeGame();
		for (StationHighlight sh : list) {
			//Iterates through the list of StationHighlights and draws circles based on the values stored in the data structure
			game.shapeRenderer.begin(ShapeType.Filled);
			game.shapeRenderer.setColor(sh.getColour());
			game.shapeRenderer.circle(sh.getStation().getPosition().getX(),
					sh.getStation().getPosition().getY(), sh.getRadius());
			game.shapeRenderer.end();
		}
	}

	/** Inner class that represents the goal station highlights */
	class StationHighlight implements Comparable<StationHighlight> {
		//This class stores the station, radius and colour of each highlight
		private final Station station;
		private final int radius;
		private final Color colour;

		StationHighlight(Station station, int radius, Color colour) {
			this.station = station;
			this.radius = radius;
			this.colour = colour;
		}

		@Override
		public int compareTo(StationHighlight o) {
			return radius - o.radius;
		}

		public Color getColour() {
			return colour;
		}

		public int getRadius() {
			return radius;
		}

		public Station getStation() {
			return station;
		}
	}

	// Create all of the Actors for all of the connections and add them to the connectionActors group
	public void addConnections(List<Connection> connections, final Color color) {
		connectionActors = new Group();
		for (Connection connection : connections) {
			final IPositionable start = connection.getStation1().getPosition();
			final IPositionable end = connection.getStation2().getPosition();
			ConnectionActor connectionActor = new ConnectionActor(Color.GRAY, start, end, CONNECTION_LINE_WIDTH);
			connection.setActor(connectionActor);
			connectionActors.addActor(connectionActor);
		}
		context.getStage().addActor(connectionActors);
	}

	/** Display the length of the connections when the game is in routing mode */
	public void drawRoutingInfo(List<Connection> connections){
		TaxeGame game = context.getTaxeGame();
		// if the game is in routing mode, then the length of the connection is displayed
		for (Connection connection : connections) {
			if (Game.getInstance().getState() == GameState.ROUTING) {
				IPositionable midpoint = connection.getMidpoint();
				game.batch.begin();
				// change colour depending on whether day or night
				if (context.getGameLogic().getPlayerManager().isNight()) {
					game.fontTinyLight.setColor(Color.WHITE);
				} else {
					game.fontTinyLight.setColor(Color.BLACK);
				}
				String text = String.valueOf(Math.round(
						context.getGameLogic().getMap().getStationDistance(connection.getStation1(),connection.getStation2())
						));
				game.fontTinyLight.draw(game.batch, text,
						midpoint.getX() - game.fontTinyLight.getBounds(text).width / 2f,
						midpoint.getY() + game.fontTinyLight.getBounds(text).height / 2f);
				game.batch.end();
			}
		}
	}

	/**This method creates a Text field at each station displaying the number of trains the current player has at that station.*/
	public void displayNumberOfTrainsAtStations() {
		//This renders the number next to each station of how many trains are located there
		TaxeGame game = context.getTaxeGame();
		game.batch.begin();
		game.fontLight.setColor(Color.BLACK);

		for (Station station : context.getGameLogic().getMap().getStations()) {
			if (trainsAtStation(station) > 0) {
				//if the number of trains at that station is greater than 0 then it renders the number in the correct place
				game.fontSmallLight.draw(game.batch, trainsAtStation(station) + "",
						(float) station.getPosition().getX() - 6,
						(float) station.getPosition().getY() + 26);
			}
		}

		game.batch.end();
	}

	/**This method counts the number of trains the current player has at a specific station.
	 * @param station The station to check.
	 * @return The number of train's the current player has at the station.
	 */
	private int trainsAtStation(Station station) {
		int count = 0;
		//This method iterates through every train and checks whether or not the location of the train matches the location of the station. Returns the number of trains at that station
		for (Player player : context.getGameLogic().getPlayerManager().getAllPlayers()) {
			for (Resource resource : player.getResources()) {
				if (resource instanceof Train) {
					if (((Train) resource).getActor() != null) {
						if (((Train) resource).getPosition().equals(station.getPosition())) {
							count++;
						}
					}
				}
			}
		}
		return count;
	}
}
