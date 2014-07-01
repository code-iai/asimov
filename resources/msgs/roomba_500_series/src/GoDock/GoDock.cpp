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
#include "roomba_500_series/GoDock.h"

GoDockAction::GoDockAction(std::string name) : as_(nh_, name, boost::bind(&GoDockAction::goalCallback, this, _1)) , action_name_(name)
{
	ir_sub_ = nh_.subscribe("/ir_character", 1, &GoDockAction::irCallback, this);
	bat_sub_ = nh_.subscribe("/battery", 1, &GoDockAction::batteryCallback, this);

	cmd_vel_pub_ = nh_.advertise<geometry_msgs::Twist>("/cmd_vel", 1);
}

GoDockAction::~GoDockAction()
{
	
}

void GoDockAction::goalCallback(const roomba_500_series::GoDockGoalConstPtr & goal)
{
	ros::Rate r(10);
	ROS_INFO("GoDock -- Initiating docking proceadure...");

	int no_signal = 0;
	ros::Time start_time = ros::Time::now();
	while(true)
	{
		// No active
		if(!as_.isActive()) return;
		// Failed
		if(ir_character_.omni == 0 && ir_character_.left == 0 && ir_character_.right == 0) no_signal++;
		else no_signal = 0;
		if(no_signal == 10)
		{
			as_.setAborted();
			ROS_INFO("GoDock - Aborting...");
			sendCmdVel(0.0, 0.0);
			return;
		}
		// Succeeded
		if(dock_)
		{
			as_.setSucceeded();
			ROS_INFO("GoDock -- Charging!");
			sendCmdVel(0.0, 0.0);
			return;
		}
		
		// TODO: Finetune the docking proceadure!!!
		if((ir_character_.left == RED_BUOY_GREEN_BUOY && ir_character_.right == RED_BUOY_GREEN_BUOY) || (ir_character_.left == RED_BUOY_GREEN_BUOY_FORCE_FIELD && ir_character_.right == RED_BUOY_GREEN_BUOY_FORCE_FIELD))
		{
			sendCmdVel(0.05, 0.0);
		}
		else if(ir_character_.left == RED_BUOY || ir_character_.left == RED_BUOY_FORCE_FIELD)
		{
			sendCmdVel(0.05, 0.1);
		}
		else if(ir_character_.right == GREEN_BUOY || ir_character_.right == GREEN_BUOY_FORCE_FIELD)
		{
			sendCmdVel(0.05, -0.1);
		}

		r.sleep();
	}	
}

void GoDockAction::irCallback(const roomba_500_series::IRCharacterConstPtr & ir)
{
	ir_character_.omni = ir->omni;
	ir_character_.left = ir->left;
	ir_character_.right = ir->right;
}

void GoDockAction::batteryCallback(const roomba_500_series::BatteryConstPtr & bat)
{
	dock_ = bat->dock;
}

void GoDockAction::sendCmdVel(float linear, float angular)
{
	geometry_msgs::Twist cmd_vel;
	cmd_vel.linear.x = linear;
	cmd_vel.angular.z = angular;
	cmd_vel_pub_.publish(cmd_vel);
}

// EOF
