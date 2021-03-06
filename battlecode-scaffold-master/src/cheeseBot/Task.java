package cheeseBot;

import battlecode.common.*;

public interface Task {

	/**
	 * Calls a single round for this Task.
	 * @return 0 when the task isn't finished, 1 when successful. Other ints are specific to the Task.
	 */
	public int run() throws GameActionException;
	
}
