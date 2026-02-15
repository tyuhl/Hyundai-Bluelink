# Hyundai Bluelink® Integration for Hubitat

Author: Tim Yuhl (@WindowWasher on the Hubitat Community Forums)

_This applcation and driver is released under the M.I.T. license. Use at your own risk_

Current version: 1.1.0

**What is it?**

Hyundai Bluelink® is a connected vehicle service that remotely collects telemetrics and allows users to issue commands to vehicles that support the service and are registered. Bluelink is implemented with server-based web services.  This package interfaces with those web services to enable Hubitat users to do things such as remotely lock or unlock their cars or remotely start the vehicle. The Hyundai Bluelink Integration package consists of a user  application and driver.

**Using this package, users can remotely:**

1. Lock or unlock the vehicle
2. Get vehicle status (doors locked, enginer off, etc.)
3. Remotely start or stop the engine with user specified climate settings (including seat climate settings where supported)
4. Get the vehicle's current location (latitude and longitude)

## Installation: ##

Hubitat Package Manager (recommended):

_HPM is an app that allows you to easily install and update custom drivers and apps on your Hubitat hub. To use HPM, you need to have it installed on your hub._

Once you have HPM installed, follow these steps to install the "Hyundai Bluelink Integration for Hubitat" application/driver:

1. In the Hubitat interface, go to Apps and select Hubitat Package Manager.
2. Select Install, then Search by Keywords.
3. Enter Hyundai in the search box and click Next.
4. Select Hyundai Bluelink Integration for Hubitat by Tim Yuhl and click Next.
5. Follow the install instructions.

Manual Installation:

1. Install the driver code in the "Drivers Code" section and the Application code in the "Application Code" section of Hubitat.


## First Use Once Installed: ##

1. Create an instance of the application in the "Apps" section of Hubitat.
2. Configure the Hyundai Bluelink Application by following the steps
 in the application. Child drivers for each vehicle registered to your Bluelink account will automatically be created after being discovered.
3. Review the Remote Start Options. Remote start options are grouped into profiles, which are used by the vehicle driver to set the options for remote engine start.

## Notes on Use: ##

1. Please do not make excessive API calls. Hyundai monitors usage and may choose to restrict your account if you make too many remote calls.
2. Some calls can take several secornds to complete (like Location). There is an option in the driver to call Refresh to read cached status (the default). Using cached data is quite fast, but data may be out of date if the vehicle has been sitting unused. Turning on the "Full Refresh" option will bypass the cache and contact the vehicle to refresh the data. "Full Refresh" commands can take up to 2 minutes, versus refresh commands that use the cached data. 

      _Warning: Bypassing the cache and using "Full Refresh" excessively could deplete the vehicle's battery._

3. Remote Start will fail if your vehicle is unlocked and it may not be logged as a failure.

## Vehicle Models That Have Been Tested With This App ##

1. Hyundai Palisade (2022 ICE)
2. Hyundai Ioniq 5 (Thanks, @lotussteve for helping out)
3. Hyundai Ioniq 5 (2022 EV)
4. Hyundai Ioniq 5 (2025 EV)
5. Hyundai Santa Fe (2026 ICE)

## Current Limitations ##

1. Only U.S. vehicles that have Bluelink are currently supported.
2. Do not become too dependent on this package. It is based on an undocumented API and could stop working at any time.

## Thanks! This package would not have been possible without: ##

1. @thecloudtaylor's excellent app/driver for the Honeywell Home WiFi Thermostats, which served as a template for this app/driver.
2. The open source Bluelinky Node.js project, which reverse-engineered the undocumented Bluelink API and supports a Node.js application. Thanks to @Hacksore and team for their great work.
3. The excellent rewrite of the climate start code by Eric (@corinus) for v1.1.0,  which enabled support for more vehicles

**_Please report any problems or bugs to the developer_**

Note: When reporting problems, please provide steps to reproduce, logs, and vehicle year and model. Make sure that logging is set to "trace". DO NOT submit logs on public-facing forums that contain your Bluelink account information.

