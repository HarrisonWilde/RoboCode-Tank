package u1600779;
import robocode.*;
import robocode.util.Utils;
import java.util.*;
import java.lang.Math.*;
import java.awt.geom.Point2D;
import java.awt.Color;

public class Roobot extends Robot
{
	// Initialise the static variables required by Roobot, these are all either self-explanatory or explained when they
	// appear in use.
	private static Hashtable<String, opponentInfo> opponents = new Hashtable<String, opponentInfo>();
	private static opponentInfo target;
	private static double scanningTimer;
	private static Point2D.Double lastPos;
	private static Point2D.Double currentPos;
	private static Point2D.Double nextPos;
	private static String situation;
	private static double angleToAvoid;

	public void run()
	{
		setColors(Color.RED, Color.ORANGE, Color.ORANGE, Color.WHITE, Color.ORANGE);
		// Gun and radar move independently from the robot's body
		setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        // Temporary arbitrary opponentInfo, lastPos, nextPos values to avoid null pointer errors until they are properly defined
		target = new opponentInfo("");
		lastPos = nextPos = new Point2D.Double(getX(), getY());
		// Roobot should initially be in scanning mode
		situation = "scanning";

		// Main robot loop
        while (true)
        {
        	// Update Roobot's current position on every call
        	currentPos = new Point2D.Double(getX(), getY());

        	// Roobot sits and spins its radar gathering information until it fires or is hit by something, at which point it 
        	// chooses a new point to move to and resumes scanning (also breaks out after 50 turns of inactivity)
        	scanningTimer = getTime();
        	while ((situation.equals("scanning")) && (getTime() - scanningTimer < 50))
        	{
	        	turnRadarRight(45);
        	}
        	pickNextPos();
        	move();
        	situation = "scanning";
        }
	}

	/** 
	* Whenever Roobot decides it needs to move, this function generates 50 random points of varying distance and degree from
	* Roobot's current position. It then rates them all using my riskFunction() and sets the nextPos to move to as the one with
	* lowest perceived risk.
	*/
	public void pickNextPos()
	{
		Point2D.Double possiblePoint;
		double possibleAngle = 360 * Math.random();
		for (int i = 0; i < 100; i++)
		{
			// First if condition signifies a call to pickNextPos() after Roobot has fired a shot so it can simply pick an
			// angle from 0 to 360; if it is called from onHitByBullet/Robot(), we want to avoid certain angles, so Roobot picks
			// angles until they lie outside of the "exclusion zone" defining where a collision occurred/bullet came from.
			if (situation.equals("fired"))
			{
				possibleAngle = 360 * Math.random();
			}
			else if (situation.equals("hitByBullet"))
			{
				do
				{
					possibleAngle = 360 * Math.random();
				} while ((Math.abs(Utils.normalRelativeAngleDegrees(possibleAngle - angleToAvoid)) < 30) || (Math.abs(Utils.normalRelativeAngleDegrees(possibleAngle - angleToAvoid)) > 150));
			}
			else if (situation.equals("hitRobot"))
			{
				do
				{
					possibleAngle = 360 * Math.random();
				} while ((Math.abs(Utils.normalRelativeAngleDegrees(possibleAngle - angleToAvoid)) < 90) || (Math.abs(Utils.normalRelativeAngleDegrees(possibleAngle - angleToAvoid)) > 110));
			}

			// Picks a random distance from 75 to 210, and combines with possibleAngle to generate a point
			possiblePoint = determinePoint(75 + 135 * Math.random(), possibleAngle);
			// nextPos will end up as the generated point with the lowest perceived risk
			if ((containedMovement(possiblePoint)) && (riskFunction(possiblePoint) < riskFunction(nextPos)))
			{
				nextPos = possiblePoint;
			}
		}

		// Keep track of the last position Roobot was at in preparation for moving to nextPos
		lastPos = currentPos;
	}

	/**
	* @param p the point which must be assessed for risk
	* Risk is summed over all alive opponents for each passed point, and is calculated based on:
	* 	- risk is initially set to the reciprocal of the distance from current and last positions, want Roobot to remain mobile
	*	- the ratio of Roobot's energy and each opponent's, multiplied by a means of determining ratio in movement angle from the
	*	  proposed nextPos to each opponent's position, low when new point is almost perpendicular to opponent position
	*	- inversely scaled with the distance from an opponent, meaning Roobot will usually move into space so as to avoid collisions
	*
	* This function is inspired by HawkOnFire referenced in the report.
	*/
	public double riskFunction(Point2D.Double p)
	{
		double risk = 0.05 / (1 + p.distance(currentPos) + p.distance(lastPos));
		double energy = getEnergy();
		for (opponentInfo opponent : opponents.values())
		{
			if (opponent.alive())
			{
				risk += (Math.min(opponent.energy() / energy, 2) * (1 + Math.abs(Math.cos(Math.toRadians(determineAngle(currentPos, p) - determineAngle(opponent.position(), p))))) / p.distanceSq(opponent.position()));
			}
		}
		return risk;
	}

	/**
	* This method first calculates:
	*   - The absolute angle from our position to the target position, this uses the standard formula for the angle of a vector
	*	  between two points
	*	- The actual angle of turn required is calculated via the provided utility function in robocode with an argument which
	*     is the difference in the robot's heading and the angleToNextPos (both absolute angles)
	*	- The distance which is easily calculated via the hypotenuse function and differences in x and y co-ordinates
	*
	* We then want to actually turn and move the robot, I have used the fact the robot can move forwards or backwards to guarantee a 
	* maximum turning angle which will not exceed 90 degrees so as to minimise the time the robot is still. This is achieved through
	* the trigonometric functions which essentially subtract 180 from any angle greater than 90 and add 180 degrees to any angles 
	* less than minus 90. A check is then performed to see whether a change was necessary to "normalise" our angle into +-90 
	* interval, in which case it is necessary for the robot to move backwards into position rather than forwards.
	*/
	public void move()
	{
		double angleToNextPos = determineAngle(currentPos, nextPos);
		double turnAngle = Utils.normalRelativeAngleDegrees(angleToNextPos - getHeading());
		double distance = Math.hypot(nextPos.getX() - getX(), nextPos.getY() - getY());

		turnRight(Math.toDegrees(Math.atan(Math.tan(Math.toRadians(turnAngle)))));
		if (Math.toRadians(turnAngle) == Math.atan(Math.tan(Math.toRadians(turnAngle))))
		{
			ahead(distance);
		}
		else
		{
			back(distance);
		}
	}

	/**
	* This method calculates where Roobot should aim and then fires, it has linear and circular targeting capability. An
	* iterative technique is employed to accurately estimate where the opponent will be and fire at that position.
	*/
	public void aimAndFire()
	{
		// Update current position in case Roobot fires during movement
		currentPos = new Point2D.Double(getX(), getY());

		// Calculates power to fire the bullet at based on distance from the target 
		double power = Math.min(900 / currentPos.distance(target.position()), 3);

		// predictedPos is initially set to the target's position, this will become a future predicted position of the target in 
		// iterativeTime ticks, which is also the time we expect for the bullet to reach predictedPos, knowing an iterativeTime value
		// then in turn increases the accuracy of our prediction for the target's eventual location. It is this feedback loop which 
		// means iterative targeting methods are more accurate.
		double iterativeTime = 0;
		Point2D.Double predictedPos = target.position();

		// This algorithm uses 20 iterations of prediction refinement
		for (int i = 0; i < 20; i++)
		{
			// time for bullet to travel is distance from predictedPos divided by bullet speed plus the time it takes to rotate the 
			// gun to the appropriate heading
			iterativeTime = (predictedPos.distance(currentPos) / (20 - (3 * power))) + (Utils.normalRelativeAngleDegrees(determineAngle(currentPos, predictedPos) - getGunHeading()) / 20);
			predictedPos = iterativeAim(iterativeTime);
		}

		// If the predicted position is not within the battlefield we should not attempt to fire at it as it will likely miss
		if (containedTargeting(predictedPos))
		{
			// Turn the gun from its current heading to the heading required to hit predictedPos
			double gunTurn = Utils.normalRelativeAngleDegrees(determineAngle(currentPos, predictedPos) - getGunHeading());

			turnGunRight(gunTurn);
			fire(power);
		}
		situation = "fired";
	}

	/**
	* @param iterativeTime, the current prediction for impact time of the bullet
	* This method is called iteratively and returns a new predicted position based on some maths and the passed iterativeTime
	*/
	public Point2D.Double iterativeAim(double iterativeTime)
	{
		// Predicted co-ordinates are calculated from the target's current position, some values are also declared here to avoid
		// excessive calls to getter functions on the target
		double predictX = target.position().getX();
		double predictY = target.position().getY();
		double headChangeRate = target.headingChangeRate();
		double heading = target.heading();
		double velocity = target.velocity();

		// Circular targeting is executed unless the turn rate is smaller than 0.01 degrees per tick in which case linear targeting
		// is used in case there is no turning rate: dividing by 0 is bad news, a turningCountRatio is also used which is the ratio
		// of number of times the target has been scanned with the number of times it has had significant turning rate. This is so
		// Roobot does not use circular targeting on targets which sometimes turn sharply as it cannot predict the rate of this.
		if ((Math.abs(headChangeRate) > 0.01) && (target.turningCountRatio() > 0.32))
		{
			// headingDiff is the current total heading change predicted in "iterativeTime" ticks
			double headingDiff = headChangeRate * iterativeTime;

			// Use parametric equations for a circle to determine change in co-ordinate required between target's current and 
			// predicted positions along an arc of a circle; the velocity divided by headingChangeRate give the radius of this circle
			predictX += (Math.cos(Math.toRadians(heading)) * velocity / headChangeRate) - (Math.cos(Math.toRadians(heading + headingDiff)) * velocity / headChangeRate);
			predictY += (Math.sin(Math.toRadians(heading + headingDiff)) * velocity / headChangeRate) - (Math.sin(Math.toRadians(heading)) * velocity / headChangeRate);
		}
		else
		{
			// Linear targeting, simple trigonometry to determine predicted position in "iterativeTime" ticks
			predictX += Math.sin(Math.toRadians(heading)) * velocity * iterativeTime;
			predictY += Math.cos(Math.toRadians(heading)) * velocity * iterativeTime;
		}

		return new Point2D.Double(predictX, predictY);
	}



// ---------- Sensory Functions ----------

	/**
	* @param e ScannedRobotEvent contains information regarding the robot's name, movement etc.
	* This function first declares an opponentInfo object and tries to set it to an opponentInfo object in opponents of the same name.
	* If it is null then a new opponentInfo object is created and added to the data structure. opponents is a hash table which will
	* eventually store all the opponents on the field. The object associated with e.getName() then has all of its information updated
	* with the information from the current scan, i.e. on each scan an opponent's info is all up to date. Roobot then decides whether
	* to change targets, and then decides if it should fire based on if this robot is its target.
	*/
	public void onScannedRobot(ScannedRobotEvent e)
	{
		opponentInfo opponent = opponents.get(e.getName());
		if (opponent == null)
		{
			opponent = new opponentInfo(e.getName());
			opponents.put(e.getName(), opponent);
		}
		opponent.update(e.getEnergy(), determinePoint(e.getDistance(), getHeading() + e.getBearing()), e.getHeading(), e.getVelocity());

		// Update target if this opponent is closer than current target or the previous target has died
		if ((e.getDistance() < currentPos.distance(target.position())) || (target.alive() == false))
		{
			target = opponent;
		}
		
		// If the scanned robot is our current target then Roobot should fire its gun provided gun heat is 0
		if ((getGunHeat() == 0) && (target.name().equals(e.getName())))
		{
			aimAndFire();
		}
	}

	/** 
	* These two methods determine when Roobot is hit and allows it to break out of "scanning mode" according to the situation so that
	* Roobot does not sit vulnerable if it is being shot by an enemy, it will instead move to a new location.
	*/
	public void onHitByBullet(HitByBulletEvent e)
	{
		situation = "hitByBullet";
		angleToAvoid = Utils.normalAbsoluteAngleDegrees(e.getHeading());
	}
	public void onHitRobot(HitRobotEvent e)
	{
		situation = "hitRobot";
		angleToAvoid = Utils.normalAbsoluteAngleDegrees(e.getBearing() + getHeading());

		// Clearly this robot is the closest opponent to Roobot so should become the target
		opponentInfo opponent = opponents.get(e.getName());
		if (opponent != null)
		{
			opponent.collisionUpdate(determinePoint(36, getHeading() + e.getBearing()), getHeading() + e.getBearing());
			target = opponent;
		}
	}

	/**
	* @param e, passed event whenever a robot dies
	* Set an opponentInfo's alive characteristic to false if the associated robot dies
	*/
	public void onRobotDeath(RobotDeathEvent e)
	{
		opponents.get(e.getName()).kill();
	}



// ---------- Helper Functions ----------

	/**
	* @param distance, the distance the generated point will be from Roobot
	* @param angle, the absolute angle of the generated point relative to Roobot
	* Determines a point based on supplied angle and distance from Roobot's current position
	*/
	public Point2D.Double determinePoint(double distance, double angle)
	{
		return new Point2D.Double(getX() + distance * Math.sin(Math.toRadians(angle)), getY() + distance * Math.cos(Math.toRadians(angle)));
	}

	/**
	* @param p, a point to be tested
	* Both of these functions simply test to see if the passed point is within a certain boundary, the top one is used for movement points
	* whilst the bottom is used for an enemies predictedPos during targeting.
	*/
	public boolean containedMovement(Point2D.Double p)
	{
		return ((p.getX() > 18) && (p.getX() < getBattleFieldWidth() - 18) && (p.getY() > 18) && (p.getY() < getBattleFieldHeight() - 18));
	}
	public boolean containedTargeting(Point2D.Double p)
	{
		return ((p.getX() > 0) && (p.getX() < getBattleFieldWidth()) && (p.getY() > 0) && (p.getY() < getBattleFieldHeight()));
	}

	/**
	* @param p1, vector origin
	* @param p2, vector end
	* Determines the absolute angle of a vector pointing from p1 to p2
	*/
	public double determineAngle(Point2D.Double p1, Point2D.Double p2)
	{
		return Math.toDegrees(Math.atan2(p2.getX() - p1.getX(), p2.getY() - p1.getY()));
	}




// ---------- Auxiliary Classes ----------

	/**
	* Class for entries of our 'opponents' hastable, to be associated with a robot's name, regarding:
	*  	- the alive status of the robot, true or false
	*  	- the robot's last known energy value
	*  	- the robot's last known position
	*	- the robot's change in heading per tick, calculating it each time update is called using previous and new heading
	*  	- the robot's last known heading
	*	- the robot's last known velocity
	*	- the time between the most up to date scan and the previous scan
	*	- the time this information is all relevant to
	*	- two counters reflecting the number of times an opponent has been scanned and the number of times they have had a
	*	  significant change in heading
	*
	* The class includes all necessary getters and a constructor, we assume that every created opponentInfo is initially essentially
	* null. There is then an update function called in the onScannedRobot() method which actually gives meaningful values to the
	* object's attributes. There is also a method for killing a robot when it dies on the field; setting its alive value to false.
	*/
	public class opponentInfo
	{
		private String name;
		private boolean alive;
		private double energy;
		private Point2D.Double position;
		private double timeBetweenScans;
		private double headingChangeRate;
		private double heading;
		private double velocity;
		private double timeScanned;
		private double updateCounter;
		private double turningCounter;

		/**
		* @param name, the name of the opponent associated with this object
		* Constructor method is called to create an "emtpy" opponentInfo object, which will then be given meaningful attribute
		* values in the onScannedRobot() method
		*/
		public opponentInfo(String name)
		{
			this.name = name;
			this.alive = false;
			this.energy = 0;
			this.position = new Point2D.Double(-100000, -100000);
			this.timeBetweenScans = 0;
			this.headingChangeRate = 0;
			this.heading = 0;
			this.velocity = 0;
			this.timeScanned = getTime();
			this.updateCounter = 0;
			this.turningCounter = 0;
		}

		/**
		* Update function to be called whenever a robot is scanned to maintain up to date information for all the passed values
		*/
		public void update(double energy, Point2D.Double position, double heading, double velocity)
		{
			this.alive = true;
			this.energy = energy;
			this.position = position;

			// Can only calculate heading change if there is a valid time difference, i.e. not the first scan of this opponent
			this.timeBetweenScans = getTime() - this.timeScanned;
			if (this.timeBetweenScans == 0)
			{
				this.headingChangeRate = 0;
			}
			else
			{
				this.headingChangeRate = (heading - this.heading) / this.timeBetweenScans;
			}

			this.heading = heading;
			this.velocity = velocity;
			this.timeScanned = getTime();

			this.updateCounter += 1;
			// Only cycle the turningCounter when the headingChangeRate is significant
			if (this.headingChangeRate > 0.01)
			{
				this.turningCounter += 1;
			}
		}

		/**
		* Special case of update called when an opponent crashes into Roobot
		*/
		public void collisionUpdate(Point2D.Double position, double heading)
		{
			this.position = position;
			this.heading = heading;
			this.velocity = 0;
		}

		/**
		* Sets alive to false for the called upon opponent robot
		*/
		public void kill()
		{
			this.alive = false;
		}

		/**
		* Used in targeting method to determine whether a robot usually turns or the current headingChangeRate is as a
		* result of an anomaly, i.e. a sharp turn at the end of linear movement.
		*/
		public double turningCountRatio()
		{
			return (this.turningCounter / this.updateCounter);
		}

		/**
		* Getter methods return the relevant attribute value when called
		*/
		public String name()				{ return this.name; }
		public boolean alive() 				{ return this.alive; }
		public double energy() 				{ return this.energy; }
		public Point2D.Double position() 	{ return this.position; }
		public double headingChangeRate()	{ return this.headingChangeRate; }
		public double heading()				{ return this.heading; }
		public double velocity()			{ return this.velocity; }
		public double timeBetweenScans()	{ return this.timeBetweenScans; }
	}
}