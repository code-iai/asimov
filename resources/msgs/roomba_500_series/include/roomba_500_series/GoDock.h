/*********************************************************************
*
* Software License Agreement (BSD License)
*
*  Copyright (c) 2010, ISR University of Coimbra.
*  All rights reserved.
*
*  Redistribution and use in source and binary forms, with or without
*  modification, are permitted provided that the following conditions
*  are met:
*
*   * Redistributions of source code must retain the above copyright
*     notice, this list of conditions and the following disclaimer.
*   * Redistributions in binary form must reproduce the above
*     copyright notice, this list of conditions and the following
*     disclaimer in the documentation and/or other materials provided
*     with the distribution.
*   * Neither the name of the ISR University of Coimbra nor the names of its
*     contributors may be used to endorse or promote products derived
*     from this software without specific prior written permission.
*
*  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
*  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
*  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
*  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
*  COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
*  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
*  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
*  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
*  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
*  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
*  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
*  POSSIBILITY OF SUCH DAMAGE.
*
* Author: Gonçalo Cabrita on 11/10/2010
*********************************************************************/
#include <ros/ros.h>
#include <actionlib/server/simple_action_server.h>
#include <tf/transform_broadcaster.h>
#include <geometry_msgs/Twist.h>		// cmd_vel
#include <roomba_500_series/IRCharacter.h>	// ir_character
#include <roomba_500_series/Battery.h>		// battery
#include <roomba_500_series/GoDockAction.h>

// IR Characters
#define FORCE_FIELD						161
#define GREEN_BUOY						164
#define GREEN_BUOY_FORCE_FIELD			165
#define RED_BUOY						168
#define RED_BUOY_FORCE_FIELD			169
#define RED_BUOY_GREEN_BUOY				172
#define RED_BUOY_GREEN_BUOY_FORCE_FIELD	173
#define VIRTUAL_WALL					162

/*! \class GoDock GoDock.h "inc/GoDock.h"
 *  \brief C++ class for the ROS GoDockAction action server.
 *
 * This sends the Roomba to the dock, obviously. Notice that if you are using move_base to move your Roomba you must cancel any existing goals or else move_base will try to move the Roomba while it tries to dock.
 */
class GoDockAction
{
	protected:
	//! Node handle
	ros::NodeHandle nh_;
	//! Action Server
	actionlib::SimpleActionServer<roomba_500_series::GoDockAction> as_;
	//! Action name
	std::string action_name_;
	
	//! Message used to published feedback
	roomba_500_series::GoDockFeedback feedback_;
	//! Message used to published result
	roomba_500_series::GoDockResult result_;

	private:
	//! Publisher for cmd_vel, to move the robot
	ros::Publisher cmd_vel_pub_;

	//! Subscriver for ir_char, to read the dock beacon info
	ros::Subscriber ir_sub_;
	//! Subscriver for battery, to check if the Roomba is docked
	ros::Subscriber bat_sub_;
	
	//! IR char
	roomba_500_series::IRCharacter ir_character_;
	//! Whether the Roomba is docked or not
	bool dock_;

	//! Send velocity commands
	/*!
	*  This function allows to send velocity commands directly to the Roomba driver. Notice that if you are using move_base to move your Roomba you must cancel any existing goals or else move_base will try to move the Roomba while it tries to dock.
	*
	*  \param linear    Linear velocity.
	*  \param angular  	Angular velocity.
	* 
	*/
	void sendCmdVel(float linear, float angular);

	public:
	//! Constructor
	/*!
	*
	*  \param name    Action Server name.
	* 
	*/
	GoDockAction(std::string name);
	//! Destructor
	~GoDockAction();

	//! Goal callback
	/*!
	*  This function is a callback for goals received.
	*
	*  \param goal    New goal.
	* 
	*/
	void goalCallback(const roomba_500_series::GoDockGoalConstPtr & goal);
	//! IR callback
	/*!
	*  This function is a callback for ir_char.
	*
	*  \param ir    Roomba ir message.
	* 
	*/
	void irCallback(const roomba_500_series::IRCharacterConstPtr & ir);
	//! Battery callback
	/*!
	*  This function is a callback for battery.
	*
	*  \param bat    Roomba battery message.
	* 
	*/
	void batteryCallback(const roomba_500_series::BatteryConstPtr & bat);
};

// EOF
