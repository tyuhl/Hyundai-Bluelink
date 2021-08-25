/**
 *  Hyundai Bluelink Driver
 *
 *  Device Type:	Custom
 *  Author: 		Tim Yuhl
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 *  files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 *  modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 *  Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 *  WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 *  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *  History:
 *  8/14/21 - Initial work.
 *
 */

String appVersion()   { return "1.0.0" }
def setVersion(){
	state.name = "Hyundai Bluelink Driver"
	state.version = "1.0.0"
}

metadata {
	definition(
			name: "Hyundai Bluelink Driver",
			namespace: "tyuhl",
			description: "Driver for accessing Hyundai Bluelink web services",
			importUrl: "https://raw.githubusercontent.com/tyuhl/Hyundai-Bluelink/main/BluelinkDriver.groovy",
			author: "Tim Yuhl")
			{
				capability "Switch"
				capability "Initialize"
				capability "Actuator"
				capability "Sensor"
				capability "Refresh"

				attribute "NickName", "string"
				attribute "VIN", "string"
				attribute "Model", "string"
				attribute "Trim", "string"
				attribute "RegId", "string"
				attribute "Odometer", "string"
				attribute "vehicleGeneration", "string"
				attribute "brandIndicator", "string"
				attribute "Engine", "string"
				attribute "DoorLocks", "string"
				attribute "Trunk", "string"
				attribute "LastRefreshTime", "string"
				attribute "locLatitude", "string"
				attribute "locLongitude", "string"
				attribute "locSpeed", "string"
				attribute "locAltitude", "string"
				attribute "locUpdateTime", "string"

				command "Lock"
				command "Unlock"
				command "Start", [[name: "profile", type: "ENUM", description: "Profile to set options", constraints: ["Summer", "Winter", "Profile3"]] ]
				command "Stop"
				command "Location"
			}

	preferences {
		section("Driver Options") {
			input("fullRefresh", "bool",
					title: "Full refresh - Set this true to query the vehicle and not use the vehicle's cached data. Warning: Setting this option means that refreshing the data can take as much as 2 minutes.",
					defaultValue: false)
			}
			section("Logging") {
				input "logging", "enum", title: "Log Level", required: false, defaultValue: "INFO", options: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
			}
	}
}

/**
 * Boilerplate callback methods called by the framework
 */

void installed()
{
	log("installed() called", "trace")
	setVersion()
	fullRefresh = false;
}

void updated()
{
	log("updated() called", "trace")
	initialize()
}

void parse(String message)
{
	log("parse called with message: ${message}", "trace")
}

/* End of built-in callbacks */

///
// Commands
///
void initialize() {
	log("initialize() called", "trace")
	refresh()
}

void refresh()
{
	log("refresh called", "trace")
	parent.getVehicleStatus(device, fullRefresh, false)
	parent. updateVehicleOdometer(device)
}

void Lock()
{
	log("Lock called", "trace")
	parent.Lock(device)
}

void Unlock()
{
	log("Unlock called", "trace")
	parent.Unlock(device)
}

void Start(String theProfile)
{
	log("Start(profile) called with profile = ${theProfile}", "trace")
	parent.Start(device, theProfile)
}

void Stop()
{
	log("Stop called", "trace")
	parent.Stop(device)
}

void Location()
{
	log("Location called", "trace")
	parent.getLocation(device)
}

///
// Supporting helpers
///
private determineLogLevel(data) {
	switch (data?.toUpperCase()) {
		case "TRACE":
			return 0
			break
		case "DEBUG":
			return 1
			break
		case "INFO":
			return 2
			break
		case "WARN":
			return 3
			break
		case "ERROR":
			return 4
			break
		default:
			return 1
	}
}

def log(Object data, String type) {
	data = "-- ${device.label} -- ${data ?: ''}"

	if (determineLogLevel(type) >= determineLogLevel(settings?.logging ?: "INFO")) {
		switch (type?.toUpperCase()) {
			case "TRACE":
				log.trace "${data}"
				break
			case "DEBUG":
				log.debug "${data}"
				break
			case "INFO":
				log.info "${data}"
				break
			case "WARN":
				log.warn "${data}"
				break
			case "ERROR":
				log.error "${data}"
				break
			default:
				log.error("-- ${device.label} -- Invalid Log Setting")
		}
	}
}

