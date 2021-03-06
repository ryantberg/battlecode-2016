package role_task_abstractionbot2.roles;

import battlecode.common.Clock;
import battlecode.common.RobotController;
import role_task_abstractionbot2.Role;
import role_task_abstractionbot2.Task;
import role_task_abstractionbot2.tasks.NonTurretDerp;
import role_task_abstractionbot2.tasks.TurretDerp;


public class Turret implements Role {
	private RobotController rc;
	Task next;
	Task other;
	
	public Turret(RobotController rc) {
		this.rc = rc;
		next = new TurretDerp(rc);
		other = new NonTurretDerp(rc);
	}
	
	@Override
	public void run() {
		int state = 0;
		while (true) {
			while(state == 0) {
		    	try {
		    		rc.setIndicatorString(1, String.valueOf(state));
		        	state = next.run();
		        	Clock.yield();
		        } catch (Exception e) {
		        	System.out.println(e.getMessage());
		        	e.printStackTrace();
		        }
	        }
			while(state == 1) {
				try {
					rc.setIndicatorString(1, String.valueOf(state));
		        	state = next.run();
		        	Clock.yield();
		        } catch (Exception e) {
		        	System.out.println(e.getMessage());
		        	e.printStackTrace();
		        }
			}
		}
	}

}
