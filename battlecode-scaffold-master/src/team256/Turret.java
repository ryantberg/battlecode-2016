package team256;

import java.util.Random;

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

public class Turret implements Role {
	private final RobotController rc;
	private final Random rand;
    private final Team myTeam;
    private final Team otherTeam;
    private Direction prevDirection = Direction.NONE;
    private MapLocation prevLocation;
    
    private MapLocation squadArchonLocation;
    private MapLocation targetEnemy;
    private boolean targetUpdated;
    private MapLocation currentOrderedGoal;
    private int squadArchon;
    private int timeSittingStillAsTTM;
    
    //Global Flags
    private boolean stopMoving = false;
    private boolean attackDen = false;
    private boolean makeRoom = false;
    
    private int minX = 0;
    private int maxX = Integer.MAX_VALUE;
    private int minY = 0;
    private int maxY = Integer.MAX_VALUE;
    
    private boolean minXFound = false;
    private boolean maxXFound = false;
    private boolean minYFound = false;
    private boolean maxYFound = false;
    
    //Constants
    private static final int NEED_RECON_RANGE = 8;
    private static final int BROADCAST_RANGE = 30; //TODO Change to distance from archon, plus small factor
    private static final int MAX_RANGE = -1;
    
	public Turret(RobotController rc){
		this.rc = rc;
		this.rand = new Random(rc.getID());
		this.myTeam = rc.getTeam();
		this.otherTeam = myTeam.opponent();
		this.prevLocation = rc.getLocation();
		RobotInfo[] friends = rc.senseNearbyRobots(MAX_RANGE, myTeam);
		RobotInfo archon = Utility.getBotOfType(friends, RobotType.ARCHON, rand, rc);
		squadArchon = archon.ID;
		squadArchonLocation = archon.location;
		timeSittingStillAsTTM = 0;
	}
	
	@Override
	public void run() {
		while(true){
			while(rc.getType() == RobotType.TURRET) {
				try {
					timeSittingStillAsTTM = 0;
					targetUpdated = false;
					handleMessages();
					if(targetEnemy != null) rc.setIndicatorDot(targetEnemy, 250, 0, 250);
					attack();
					checkForRecon();
					if (Utility.chance(rand, .33)) {
						RobotInfo[] adjFriends = rc.senseNearbyRobots(10, myTeam);
						if (Utility.getNumberOfBotOfType(adjFriends, RobotType.TURRET ) >= 6) {
							rc.pack();
						}
					}
				} catch (Exception e) {
		            System.out.println(e.getMessage());
		            e.printStackTrace();
		    	}
				Clock.yield();
			}
			while(rc.getType() == RobotType.TTM) {
				try {
					//TODO Fix it so if turrets cant get within range they unpack anyways


					if (rc.getLocation() == prevLocation) {
						timeSittingStillAsTTM += 1;
					}
					if (timeSittingStillAsTTM > 100) {
						rc.unpack();
					}
					
					this.prevLocation = rc.getLocation();
					
					RobotInfo[] hostilesNearby = rc.senseHostileRobots(rc.getLocation(), -1);
					if(hostilesNearby.length > 0) { //unpack if there are enemies nearby
						rc.unpack();
					} else if (rc.isCoreReady() && makeRoom) {
						prevDirection=Utility.tryToMoveDontClear(rc, rc.getLocation().directionTo(squadArchonLocation).opposite(),prevDirection);
					} else if (currentOrderedGoal != null && rc.getLocation().distanceSquaredTo(currentOrderedGoal) < RobotType.TURRET.sensorRadiusSquared) {
						rc.setIndicatorString(0, ""+rc.getLocation().distanceSquaredTo(currentOrderedGoal));
						handleMessages();
						rc.unpack();		
					} else if(currentOrderedGoal != null) {
						rc.setIndicatorString(0, ""+rc.getLocation().distanceSquaredTo(currentOrderedGoal));
						handleMessages();
						prevDirection=Utility.tryToMoveDontClear(rc, rc.getLocation().directionTo(currentOrderedGoal),prevDirection);
					} else {
						Direction dirToGo = Utility.getRandomDirection(rand);
						prevDirection=Utility.tryToMoveDontClear(rc, dirToGo,prevDirection);
						if (Utility.chance(rand, .7)) {
							RobotInfo[] adjFriends = rc.senseNearbyRobots(10, myTeam);
							if (Utility.getNumberOfBotOfType(adjFriends, RobotType.TURRET ) < 6) {
								rc.unpack();
							}
						}
					}			
				} catch (Exception e) {
		            System.out.println(e.getMessage());
		            e.printStackTrace();
		    	}
				Clock.yield();
			}
		}
	}
	//~~~~~~~~~~~~~~~~~~END MAIN LOOP~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Handles the contents of the signal queue
	 * @throws GameActionException 
	 */
	public void handleMessages() throws GameActionException {
		Signal[] messages = rc.emptySignalQueue();
		for(Signal message : messages) {
			if(message.getTeam().equals(myTeam)){ //Friendly message
				int[] contents = message.getMessage();
				if(contents != null) { //Not a basic signal
					int id = message.getID();
					int code = Comms.getMessageCode(contents[0]);
					int aux = Comms.getAux(contents[0]);
					MapLocation loc = Comms.decodeLocation(contents[1]);
					if (id == squadArchon) {
						squadArchonLocation = message.getLocation(); //Update squad archon location
					}
					switch (code){
						case Comms.TURRET_ATTACK_HERE:
								if(loc.distanceSquaredTo(rc.getLocation()) <= RobotType.TURRET.attackRadiusSquared) {
									targetEnemy = loc;
									targetUpdated = true;
								}
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
						case Comms.TURRET_MOVE:
							if (rc.getType() == RobotType.TURRET) {
								rc.pack();
								currentOrderedGoal = loc;
							}	
							break;
						case Comms.TURRET_STOP:
							if (rc.getType() == RobotType.TTM) {
								currentOrderedGoal = null;
								rc.unpack();
							}
							break;
						case Comms.ATTACK_DEN:
							if (rc.getType() == RobotType.TURRET) {
								attackDen = true;
								currentOrderedGoal = loc;
								rc.pack();
							}
							break;
						case Comms.MAKE_ROOM:
							if (rc.getType() == RobotType.TURRET) {
								makeRoom = true;
								rc.pack();
							}
							break;
					}
				}
			}
		}
	}
	
	/**
	 * Deals damage to optimal target (if possible)
	 * @throws GameActionException
	 */
	public void attack() throws GameActionException {
		if(targetUpdated && rc.isWeaponReady() && rc.canAttackLocation(targetEnemy)) { //Snipe sighted
			rc.attackLocation(targetEnemy);
		}
		else {
			RobotInfo[] enemiesWithinRange = rc.senseHostileRobots(rc.getLocation(), RobotType.TURRET.attackRadiusSquared);
			if(enemiesWithinRange.length > 0) { //We're in combat
				RobotInfo nearTargetEnemy = Utility.getTarget(enemiesWithinRange, GameConstants.TURRET_MINIMUM_RANGE, rc.getLocation());
				if(rc.isWeaponReady() && nearTargetEnemy != null) {
					rc.attackLocation(nearTargetEnemy.location);
				}
			}
		}
	}
	
	/**
	 * Checks for available recon, and requests if necessary.
	 * @throws GameActionException 
	 */
	public void checkForRecon() throws GameActionException {
		RobotInfo[] nearbyFriendlies = rc.senseNearbyRobots(NEED_RECON_RANGE, myTeam);
		boolean weGood = false;
		for(RobotInfo friendly : nearbyFriendlies) {
			if(friendly.type.equals(RobotType.SCOUT)) {
				weGood = true;
				break;
			}
		}
		if(!weGood && rc.getRoundNum() % 3 == 1) {
			rc.broadcastSignal(BROADCAST_RANGE);
		}
	}
	
}
