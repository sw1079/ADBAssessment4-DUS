package fvs.taxe.controller;

import fvs.taxe.TaxeGame;
import gameLogic.GameState;
import gameLogic.listeners.ConnectionChangedListener;
import gameLogic.listeners.StationChangedListener;
import gameLogic.map.CollisionStation;
import gameLogic.map.Connection;
import gameLogic.map.Position;
import gameLogic.map.Station;
import gameLogic.resource.KamikazeTrain;
import gameLogic.resource.PioneerTrain;

import java.util.ArrayList;

import Util.TextEntryDialog;
import Util.Tuple;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

/** Class that represents all of the connections been made on the map at that time*/
public class ConnectionController {
	/** Context that the connection controller acts in*/
	private Context context;

	/** Array of all listeners that are waiting to be notified of a connection change (added or removed) */
	private static ArrayList<ConnectionChangedListener> listeners = new ArrayList<ConnectionChangedListener>();
	
	/** Array of all listeners that are waiting to be notified of a station change (added or removed) */
	private static ArrayList<StationChangedListener> slisteners = new ArrayList<StationChangedListener>();

	/** Array of connections currently being made in the map*/
	private static ArrayList<Connection> connections = new ArrayList<Connection>();

	/** Input adapter for taking input when giving the name of the station*/
	private InputAdapter nameip;

	/** The clickable cancel button*/
	private ImageButton cancel;
	
	/** The image that represents the cancel button*/
	private Image cancelImage;

	/** The next junction number given when creating new junctions */
	private static int junctionNumber = 0;

	/** The pioneer train controller that is currently active (null if none) */
	private PioneerTrainController controller;

	/** The pioneer train that is currently active (null if none) */
	private PioneerTrain train;
	
	/** The station that corresponds to the first station of the active train*/
	private Station firstStation;

	/** Constructor for COnnectionController. Has no effect until beginCreating() called */
	public ConnectionController(final Context context) {
		this.context = context;
	}

	/** Begin the connection creation mode 
	 * Show only the pioneer train, cancel button
	 * @param train Pioneer train that is creating the connection
	 */
	public void beginCreating(PioneerTrain train) {
		this.train = train;
		this.firstStation = train.getLastStation();
		controller = new PioneerTrainController(train, context);
		controller.beginCreating();
		context.getGameLogic().setState(GameState.CREATING_CONNECTION);
		
		TrainController trainController = new TrainController(context);
		trainController.setTrainsVisible(train, false);

		context.getTopBarController().displayMessage("Select the destination station", Color.BLACK);
		drawCancelButton();
	}

	/** End the connection creation mode
	 * Clear all variables, show all trains and go back to normal mode
	 * @param connection The connection that has been created (null if none created)
	 */
	public void endCreating(Connection connection) {
		// Only add the connection if it is null
		if (connection != null){
			connections.add(connection);
		} else {
			// if no connection given, hide the train (as it will be still be at station)
			train.getActor().setVisible(false);
		}
		
		// reset the variables
		this.train = null;
		this.firstStation = null;
		this.controller = null;
		
		TrainController trainController = new TrainController(context);
		trainController.setTrainsVisible(train, true);
		cancel.setVisible(false);
		cancelImage.setVisible(false);

		context.getGameLogic().setState(GameState.NORMAL);
		context.getTopBarController().clearMessage(); // prevents it showing "select destination station"
	}

	/** Create and correctly render a new station. Notify all stationChanged listeners 
	 * @param string Name of the new station (given by user)
	 * @param location Location of the new station
	 * @return The stationt hat is created
	 */
	private Station createNewStation(String string, Position location) {
		Station station = new Station(string, location); 
		StationController.renderStation(station);
		stationAdded(station);
		return station;
	}

	/** Destroy the connection that the train is currently on (and the train)
	 * @param train The kamikaze train that will destroy the connnection
	 */
	public void destroyConnection(KamikazeTrain train) {
		Station l1 = train.getLastStation();
		Station l2 = train.getNextStation();
		Connection connection = context.getGameLogic().getMap().getConnection(l1, l2);

		connectionRemoved(connection); // notify all listeners that the connection has been removed

		removeIsolatedJunctions(l1);
		removeIsolatedJunctions(l2);
	}

	/** If the junction is left isolated (no connections) remove it, otherwise do nothing
	 * @param l1 CollisionStation to test if there is any connections to it
	 */
	public void removeIsolatedJunctions(Station l1) {
		// test to see if any junctions are left isolated- if they are, remove them
		if (l1.getClass().equals(CollisionStation.class)) {
			if (!context.getGameLogic().getMap().hasConnection(l1)) {
				stationRemoved(l1);
			}
		}
	}

	/** Test if the connection between station and firstStation is already being made
	 * @param station Station at the other end of the connection
	 * @return Whether a connection is being made
	 */
	protected boolean connectionBeingMade(Station station) {
		for (Connection connection: connections) {
			// connections saved as 1 way therefore test both cases
			if (connection.getStation1().equals(firstStation)){
				if (connection.getStation2().equals(station)){
					return true;
				}
			} else if (connection.getStation2().equals(firstStation)){
				if (connection.getStation1().equals(station)){
					return true;
				}
			}
		}
		return false;
	}

	/** draw a line from the trains first station to the current mouse position
	 * will draw black if day, white if night
	 * will only draw in creating_connection mode */
	public void drawMouse() {
		ShapeRenderer shapeRenderer = context.getTaxeGame().shapeRenderer;
		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
		context.getGameLogic().getPlayerManager();
		if (context.getGameLogic().getPlayerManager().isNight()){
			shapeRenderer.setColor(Color.WHITE);
		} else {
			shapeRenderer.setColor(Color.BLACK);
		}

		context.getTaxeGame();
		shapeRenderer.rectLine(firstStation.getPosition().getX(), firstStation.getPosition().getY(), 
				Gdx.input.getX(), TaxeGame.HEIGHT- Gdx.input.getY(), 5);
		shapeRenderer.end();
	}

	/** Add the button and image that corresponds to cancelling connection creation */
	private void drawCancelButton() {
		//Adds a button to leave the screen
		if (cancel == null) {
			Texture cancelText = new Texture(Gdx.files.internal("btn_cancel.png"));
			// image representing button
			cancelImage = new Image(cancelText);
			cancelImage.setWidth(106);
			cancelImage.setHeight(37);
			cancelImage.setPosition(TaxeGame.WIDTH - 120, TaxeGame.HEIGHT - 56);

			// actual cancel button
			cancel = new ImageButton(context.getSkin());
			cancel.setPosition(TaxeGame.WIDTH - 120, TaxeGame.HEIGHT - 56);
			cancel.setWidth(106);
			cancel.setHeight(37);
			cancel.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					endCreating(null); // cancel creation mode
					controller.setActive(true); // stops clicks being registered by pioneerTrainController
					
				}
			});
			
			context.getStage().addActor(cancelImage);
			context.getStage().addActor(cancel);
		} else {
			// dont re-add to stage if cancelling already exists, just change visibility
			cancel.setVisible(true);
			cancelImage.setVisible(true);
		}
	}

	/** Display the station naming box and start getting input to it
	 * @param location The location that will correspond to the new station
	 */
	public void showStationNameEntry(final Position location) {
		final InputProcessor ip = Gdx.input.getInputProcessor();
		Image image = new Image(new Texture(Gdx.files.internal("NewStationDialog.png")));
		
		// create a new text entry dialog that will take station name and name it correctly
		nameip = new TextEntryDialog(context, image) {
			@Override
			public boolean keyDown(int keycode) {
				super.keyDown(keycode);
				if (keycode == Keys.ENTER){
					// finish naming when enter pressed
					String text = textEntryBar.getLabelValue();
					if (context.getGameLogic().getMap().isUniqueName(text)) {
						// only create station if name is unique
						Station station = createNewStation(text, location);
						controller.endCreating(station);
						textEntryBar.clear();
						setVisible(false); // hide the dialog box
						Gdx.input.setInputProcessor(ip); // change the input processor back to the old one

					} else {
						context.getTopBarController().displayFlashMessage("Please enter a unique station name", Color.RED);
					}
				}
				if (keycode == Input.Keys.ESCAPE) {
					// cancel name entry if escape pressed
					context.getGameLogic().setState(GameState.CREATING_CONNECTION);
					setVisible(false);
					Gdx.input.setInputProcessor(ip);
				}
				return true;
			}
		};
		Gdx.input.setInputProcessor(nameip);
	}

	/** Reorganise connections so that any connection overlaps are changed into a junction with new connections
	 * @param collidedPositions List of pairs of connections that will be removed, and where collision took place (ordered by distance from firstSTation
	 * @param connection The connection that the positions collide with
	 */
	public void addNewConnections(ArrayList<Tuple<Connection, Position>> collidedPositions, Connection connection) {
		// Iterates through the list of collidedPositions to progressively build up the new correct junctions and conenctions
		CollisionStation prevJunction = null; 
		Station startStation = connection.getStation1();
		Station endStation = connection.getStation2();
		for (int i = 0; i < collidedPositions.size(); i++) {
			Tuple<Connection, Position> pair = collidedPositions.get(i);
			
			// create a new collision station atthe location of the collision
			Position position = pair.getSecond();
			CollisionStation junction = context.getGameLogic().getMap().addJunction(ConnectionController.getNextJunctionNum(), position);
			StationController.renderCollisionStation(junction);

			// Remove the connection that the given connection has collided with
			Connection collidedConn = pair.getFirst();
			connectionRemoved(collidedConn);

			Station cStation1 = collidedConn.getStation1(); 
			Station cStation2 = collidedConn.getStation2();
			if (i == 0 && collidedPositions.size() == 1) {
				// if there is only one connection, only one junction is required therefore only 4 connections needed
				// one from each of the 4 stations to the newly created junction
				connectionAdded(new Connection(cStation1, junction)); 
				connectionAdded(new Connection(cStation2, junction));
				connectionAdded(new Connection(startStation, junction));
				connectionAdded(new Connection(endStation, junction));

			} else if (i == 0) {
				// if there is more than one connection and it is the first collided connection, create 3 connections to the new junction
				
				// one from each of the collidedConnections stations
				connectionAdded(new Connection(cStation1, junction));
				connectionAdded(new Connection(cStation2, junction));
				connectionAdded(new Connection(startStation, junction)); // one from firstStation to the junction 
				// leave the 4th connection, as it will connect to another junction not yet created
				prevJunction = junction;

			} else if (i == collidedPositions.size() -1) {
				// if there is more than one connection and it is the final connection, then 4 connections needed

				// one each from the collidedCOnnections stations to the junction
				connectionAdded(new Connection(cStation1, junction));
				connectionAdded(new Connection(cStation2, junction));
				connectionAdded(new Connection(prevJunction, junction)); // one from the previous junction to the new junction
				connectionAdded(new Connection(junction, endStation)); // one from the junction to the final station

			} else {
				// if there is more than one connection, and it is an intermediate between 2 junctions (ie not first or final connection)
				// create 3 new connections
				
				// one each from the collidedConnections stations to the junction
				connectionAdded(new Connection(cStation1, junction));
				connectionAdded(new Connection(cStation2, junction));
				connectionAdded(new Connection(prevJunction, junction)); // one from the old junction to the new junction 
				// leave the 4th connection, as it will connect to another junction not yet created
				prevJunction = junction;
			} 
		}
	}

	/** Get the string that corresponds to the next junction number
	 * @return String that corresponds to the next junction number
	 */
	public static String getNextJunctionNum() {
		String string = Integer.toString(junctionNumber);
		junctionNumber+=1;
		return string;
	}

	public static void subscribeConnectionChanged(ConnectionChangedListener connectionChangedListener) {
		listeners.add(connectionChangedListener);
	}

	public void connectionAdded(Connection connection){
		for (ConnectionChangedListener listener: listeners){
			listener.added(connection);
		}
	}

	public void connectionRemoved(Connection connection){
		for (ConnectionChangedListener listener: listeners){
			listener.removed(connection);
		}
	}

	public static void subscribeStationAdded(StationChangedListener stationAddedListener){
		slisteners.add(stationAddedListener);
	}

	private void stationAdded(Station station) {
		for (StationChangedListener listener : slisteners ){
			listener.stationAdded(station);
		}
	}

	private void stationRemoved(Station station){
		for (StationChangedListener listener : slisteners ){
			listener.stationRemoved(station);
		}
	}
}
