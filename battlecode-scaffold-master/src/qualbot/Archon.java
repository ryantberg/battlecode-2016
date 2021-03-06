package qualbot;

import java.util.ArrayList;
import java.util.HashMap;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Signal;
import battlecode.common.Team;
import team256.Utility;
import team256.Utility.Tuple;


public class Archon extends Role {
	protected ArrayList<MapLocation> dens;
	private HashMap<MapLocation, Integer> parts;
	private ArrayList<MapLocation> neutrals;
	double prevHealth;
    private boolean beingAttacked = false;
    private boolean beingSniped = false;
    private Direction fleeDirection = null;
    private int fleeTimeout = 10;
    private boolean turtle = false;
    private MapLocation lastSeenEnemyLoc = null;
    private MapLocation objectiveLocation;
	
	protected int objectiveMargin = 0;
	private int DPS_RETREAT_THRESHOLD = 0;
    private double ACTIVATE_BOT_THRESHOLD = 10;
    private double GET_PARTS_THRESHOLD = .033;
	
	public Archon(RobotController rc) {
		super(rc);
		this.dens = new ArrayList<MapLocation>();
		this.parts = new HashMap<MapLocation, Integer>();
		this.neutrals = new ArrayList<MapLocation>();
		this.prevHealth = rc.getHealth();
	}

	@Override
	public void run() {
		while(true) {
			try {
				myLocation = rc.getLocation();
				beingAttacked = (rc.getHealth() < prevHealth);
				prevHealth = rc.getHealth();
				RobotInfo[] friendsInSight = rc.senseNearbyRobots(-1, myTeam);
				RobotInfo[] enemies = rc.senseHostileRobots(rc.getLocation(), -1);
				
				if (enemies.length == 0 && beingAttacked) {
					beingSniped = true;
					fleeTimeout = 10;
				} else if (enemies.length > 0){
					beingSniped = false;
					lastSeenEnemyLoc = enemies[0].location;
				} else if (fleeTimeout <= 0){
					fleeTimeout -= 1;
				} else {
					beingSniped = false;
				}
				
				if (beingSniped) {
					if (lastSeenEnemyLoc != null) {
						Direction dirToGo = rc.getLocation().directionTo(lastSeenEnemyLoc).opposite();
						tryToMove(dirToGo);
					} else {
						Direction dirToGo = getRandomDirection();
						tryToMove(dirToGo);
					}
				}
				
				//TEST CODE PLEASE IGNORE
				if(rc.getTeamParts() > 130 && chance(0.3)) {
					if(chance(0.75)) tryToBuild(RobotType.SOLDIER);
					else if(chance(0.25)) tryToBuild(RobotType.GUARD);
					else if(chance(0.7)) tryToBuild(RobotType.SCOUT);
					else tryToBuild(RobotType.TURRET);
				}
				
				if (rc.isCoreReady()) {
					Tuple<MapLocation, Double> partsTup = searchForParts();
					MapLocation partsLoc = partsTup.x;
					double partsValue = partsTup.y; //Not actually amount of parts, but heuristic value
					Tuple<RobotInfo, Double> neutralBotTup = searchForNeutral();
					RobotInfo neutralBot = neutralBotTup.x;
					double neutralValue = neutralBotTup.y;
					
					MapLocation randomFlag = null;
					if (neutralValue != 0) {
						if (rc.getLocation().isAdjacentTo(neutralBot.location) && rc.isCoreReady()) { //Magic indicating adjacent bot
							rc.activate(neutralBot.location);
						} else if (neutralValue > ACTIVATE_BOT_THRESHOLD || (neutralBot != null && enemies.length == 0)) { //Magic
							if (neutralBot != null)
								randomFlag = neutralBot.location;
								gotoObjective(randomFlag, 0, objectiveMargin+15, friendsInSight);
						} 
					}
					if (partsValue / rc.getTeamParts() > GET_PARTS_THRESHOLD || (enemies.length == 0)) { //Magic
						if (partsLoc != null) {
							randomFlag = partsLoc;
							gotoObjective(randomFlag, 0, 0, friendsInSight);
						}
					}
				}
				handleMessages();
				healAlly();
				myLocation = rc.getLocation();
				
				flee(enemies);
				
				
					gotoObjective(objectiveFlag, 49, 350, friendsInSight);
			} catch (Exception e) {
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	    	}
			Clock.yield(); //TODO: Move this? Exceptions waste a round this way
			
		}
	}
	
	private void anyGoodies(RobotInfo[] friendsInSight) throws GameActionException {
		RobotInfo[] neutralBots = rc.senseNearbyRobots(-1, Team.NEUTRAL);
		MapLocation[] partsLocations = rc.sensePartLocations(-1);
		MapLocation closestGoodie = null;
		int closestDistance = 9999;
		for (RobotInfo bot: neutralBots) {
			int distance = bot.location.distanceSquaredTo(myLocation);
			if (distance < closestDistance) {
				closestGoodie = bot.location;
				closestDistance = distance;
			}
		}
		for (MapLocation parts: partsLocations) {
			int distance = parts.distanceSquaredTo(myLocation);
			if (distance < closestDistance) {
				closestGoodie = parts;
				closestDistance = distance;
			}
		}
		for (MapLocation bot: neutrals) {
			int distance = bot.distanceSquaredTo(myLocation);
			if (distance < closestDistance) {
				closestGoodie = bot;
				closestDistance = distance;
			}
		}
		for (MapLocation part: parts.keySet()) {
			int distance = part.distanceSquaredTo(myLocation);
			if (distance < closestDistance) {
				closestGoodie = part;
				closestDistance = distance;
			}
		}
		objectiveFlag = closestGoodie;
		if (objectiveFlag != null) {
			gotoObjective(objectiveFlag, 0, objectiveMargin+15, friendsInSight);
		}
	}
	
	/**
	 * Returns the closests/best neutral bot in range.
	 * @return Tuple of the form Tuple.x = bestBot and Tuple.y = bestValue
	 */
	private Tuple<RobotInfo, Double> searchForNeutral() {
		RobotInfo[] neutralBots = rc.senseNearbyRobots(-1, Team.NEUTRAL);
		RobotInfo bestBot = null;
		double bestValue = 0;
		for(RobotInfo robot : neutralBots) {
			//TODO Create actual heuristic
			double distance = rc.getLocation().distanceSquaredTo(robot.location);
			if (distance <= 2) distance = 0.0001; //Magic for having it pick closebots within range instantly
			double value = rc.getHealth() / distance; //Temp heuristic that should value archons
			if (value > bestValue) {
				bestValue = value;
				bestBot = robot;
			}
		}
		
		Tuple<RobotInfo, Double> robotAndValue = new Tuple<>(bestBot, bestValue);
		return robotAndValue;
	}
	
	/**
	 * Detects the largest/closest parts pile in sight.
	 * @return Tuple of form x = partsLocation, y = numberOfParts.
	 * @throws GameActionException 
	 * TODO Expensive when there are a ton of parts. Figure way of decreasing cost
	 */
	private Tuple<MapLocation, Double>  searchForParts() throws GameActionException {
		int bytes = Clock.getBytecodeNum();
		MapLocation[] partsLocations = rc.sensePartLocations(-1);
		MapLocation bestPartsPile = null;
		double maxParts = 0;
		for(MapLocation location : partsLocations) {
			//TODO Optimize best parts heuristic, such as adding in penalty for parts being in rubble
			int distance = rc.getLocation().distanceSquaredTo(location);
			double parts = rc.senseParts(location) / Math.pow(distance, .5);
			if (rc.senseRobotAtLocation(location) != null) parts = 0; //Robot built on top, so let's not try to get it.
			if (rc.senseRubble(location) > GameConstants.RUBBLE_OBSTRUCTION_THRESH) {
				parts /= 20; //Magic
			} 
			if(parts > maxParts) {
				bestPartsPile = location;
				maxParts = parts;
			}
		}
		rc.setIndicatorString(0, "Byte cost: " + (Clock.getBytecodeNum() - bytes));
		Tuple<MapLocation, Double> locAndParts = new Tuple<>(bestPartsPile, maxParts);
		return locAndParts;
	}
	
	private void flee(RobotInfo[] enemies) throws GameActionException {
		int[] slice = {0};
		Tuple<Direction, Double> dpsDirTuple = getDirectionOfMostDPS(enemies, slice);
		if (dpsDirTuple != null) {
			Direction dirDps = dpsDirTuple.x;
			double dps = dpsDirTuple.y;
			if (dps > DPS_RETREAT_THRESHOLD) {
				tryToMove(dirDps.opposite());
			}
		}
	}
	
	@Override
	protected void handleMessage(Signal message) {
		if(message.getTeam().equals(myTeam)) {
			int[] contents = message.getMessage();
			if(contents != null) { //Advanced message
				int code = Comms.getMessageCode(contents[0]);
				int aux = Comms.getAux(contents[0]);
				MapLocation loc = Comms.decodeLocation(contents[1]);
				switch (code){
					case Comms.DEN_FOUND:
						if(!dens.contains(loc)) dens.add(loc);
						break;
					case Comms.FOUND_MINX:
						if(!minXFound) {
							minX = aux;
							minXFound = true;
						}
						break;
					case Comms.FOUND_MAXX:
						if(!maxXFound) {
							maxX = aux;
							maxXFound = true;
						}
						break;
					case Comms.FOUND_MINY:
						if(!minYFound) {
							minY = aux;
							minYFound = true;
						}
						break;
					case Comms.FOUND_MAXY:
						if(!maxYFound) {
							maxY = aux;
							maxYFound = true;
						}
						break;
					case Comms.ATTACK_DEN:
						if(state == IDLE) {
							objectiveFlag = loc;
							state = SIEGING_DEN;
						}
						break;
					case Comms.DEN_DESTROYED:
						if(state == SIEGING_DEN && objectiveFlag.equals(loc)) {
							state = IDLE;
						}
						break;
					case Comms.ATTACK_ENEMY:
						if(state == IDLE) {
							objectiveFlag = loc;
							state = SIEGING_ENEMY;
						}
						break;
					case Comms.PARTS_FOUND:
						parts.put(loc, aux);
						break;
					case Comms.NEUTRAL_FOUND:
						if(!neutrals.contains(loc)) neutrals.add(loc);
						break;
				}
			}
			else { //Basic message
				
			}
		}
	}
	
	/**
	 * Heals the weakest nearby friendly
	 * @throws GameActionException 
	 */
	public void healAlly() throws GameActionException {
		RobotInfo[] nearbyRobots = rc.senseNearbyRobots(attackRadiusSquared, myTeam);
		RobotInfo weakestFriend = null;
		if (nearbyRobots.length > 0) {
        	double minHealth = Double.POSITIVE_INFINITY;
        	for (RobotInfo curBot : nearbyRobots){
        		if (curBot.health < minHealth && curBot.type != RobotType.ARCHON) {
        			minHealth = curBot.health;
        			weakestFriend = curBot;
        		}
        	}
		}
		if (weakestFriend != null) {
        	rc.repair(weakestFriend.location);
        }
	}
	
	private void tryToBuild(RobotType typeToBuild) throws GameActionException{
		if(rc.isCoreReady()) {
			for(int i = 0; i < 8; i++) {
				if(rc.canBuild(directions[i], typeToBuild)) { 
					rc.build(directions[i], typeToBuild);
					break;
				}
			}
		}
	}
	
	/**
	 * Determines in which direction, given a group of robots, has the most dps
	 * TODO Make changes to the slice of area considered for dps and have it poss as a variable
	 * @param robotsToSearch
	 * @param rc
	 * @param viewToConsider integer list such as [-1, 0, 1] that includes the offsets in the direction 
	 * aka [0] only looks in that direction and [-4,-3,..,3,4] looks everywhere
	 * @return Returns a tuple such that Tuple.x = direction and Tuple.y = dps of that direction 
	 */
	public Tuple<Direction, Double> getDirectionOfMostDPS(RobotInfo[] robotsToSearch, int[] viewToConsider) {
		MapLocation myLocation = rc.getLocation();
		double[] dpsInDirection = new double[8];
		for (RobotInfo robot : robotsToSearch) {
			Direction dirToRobot = myLocation.directionTo(robot.location);
			double attackDelay = robot.type.attackDelay;
			if (attackDelay <= 0)
				attackDelay = 1;
			double dps = robot.attackPower / attackDelay;
			dpsInDirection[dirToRobot.ordinal()] += dps;
		}
		int maxIndex = -1;
		double maxValue = 0;
		int widerView[] = new int[8];
		//Directional Offsets
		for (int i = 0; i < 8; i++) {
			for (int offset : viewToConsider) {
				widerView[i] = (int) dpsInDirection[(i+offset+8)%8];
			}
		}
		for (int i = 0; i < 8; i++) {
			if (widerView[i] > maxValue) {
				maxValue = dpsInDirection[i];
				maxIndex = i;
			}
		}
		if (maxIndex == -1) {
			return null;
			
		} else {
			Direction dir = directions[maxIndex];
			double maxDps = dpsInDirection[maxIndex];
			Tuple<Direction, Double> dirAndDps = new Tuple<>(dir, maxDps);
			return dirAndDps;
		}
	}
	
}
