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
 *  5/2/25  - Added additional attributes
 *  5/5/25  - Added additional attributes and some EV support
 *  5/10/25 - Added some EV attributes and an EV status HTML attribute
 *  10/18/25 - Fixed EV Start and Stop and added EVBatteryPluggedIn attribute (thx corinuss)
 */

String appVersion()   { return "1.0.5-beta.climate.1" }
def setVersion() {
	state.name = "Hyundai Bluelink Driver"
	state.version = appVersion()
}

metadata {
	definition(
			name: "Hyundai Bluelink Driver",
			namespace: "tyuhl",
			description: "Driver for accessing Hyundai Bluelink web services",
			importUrl: "https://raw.githubusercontent.com/tyuhl/Hyundai-Bluelink/main/BluelinkDriver.groovy",
			author: "Tim Yuhl")
			{
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
				attribute "Hood", "string"
				attribute "Trunk", "string"
				attribute "LastRefreshTime", "string"
				attribute "locLatitude", "string"
				attribute "locLongitude", "string"
				attribute "Range", "string"
				attribute "isEV", "string"
				attribute "BatterySoC", "string"
				attribute "locUpdateTime", "string"
				attribute "EVBattery", "string"
				attribute "EVBatteryCharging", "string"
				attribute "EVBatteryPluggedIn", "string"
				attribute "EVRange", "string"
				attribute "TirePressureWarning", "string"
				attribute "statusHtml", "string"
				attribute "EVstatusHtml", "string"

				command "Lock"
				command "Unlock"
				command "Start", [[name: "profile", type: "ENUM", description: "Profile to set options", constraints: ["Summer", "Winter", "Profile3"]] ]
				command "Stop"
				command "Location"
			}

	preferences {
		section("Driver Options") {
			input("fullRefresh", "bool",
					title: "Full refresh - Turn on this option to directly query the vehicle for status instead of using the vehicle's cached status. Warning: Turning on this option will result in status refreshes that can take as long as 2 minutes.",
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
	setVersion()
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
	updateHtml()
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
// Data managed by the App but stored in the device
///
void setClimateCapabilities(Map climate_capabilities)
{
	state.climateCapabilities = climate_capabilities
}

Map getClimateCapabilities()
{
	return state.climateCapabilities
}

void setClimateProfiles(Map profiles)
{
	state.climateProfiles = profiles
}

Map getClimateProfiles()
{
	return state.climateProfiles
}

///
// Supporting helpers
///
private void updateHtml()
{
	Boolean isEV = device.currentValue("isEV") == "true"
	//regular statusHtml
	def builder = new StringBuilder()
	builder << "<table class=\"bldr-tbl\">"
	String statDoors = device.currentValue("DoorLocks")
	builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Doors:" + "</td><td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + statDoors + "</td></tr>"
	String statHood = device.currentValue("Hood")
	builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Hood:" + "</td><td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + statHood + "</td></tr>"
	String statTrunk = device.currentValue("Trunk")
	builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Trunk:" + "</td><td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + statTrunk + "</td></tr>"
	String statEngine = device.currentValue("Engine")
	builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Engine:" + "</td><td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + statEngine + "</td></tr>"
	String statRange = device.currentValue("Range")
	builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Range:" + "</td><td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + statRange + " miles</td></tr>"
	builder << "</table>"
	String newHtml = builder.toString()
	sendEvent(name:"statusHtml", value: newHtml)
	//EV stuff
	if (isEV) 
	{
		def builder2 = new StringBuilder()
		builder2 << "<table class=\"bldr2-tbl\">"
		String evBattery = device.currentValue("EVBattery")
		builder2 << "<tr><td class=\"bldr2-label\" style=\"text-align:left;\">" + "Battery:" + "</td><td class=\"bldr2-text\" style=\"text-align:left;padding-left:5px\">" + evBattery + " %" + "</td></tr>"
		String statPluggedIn = (device.currentValue("EVBatteryPluggedIn") == "true") ? "Yes" : "No"
		builder2 << "<tr><td class=\"bldr2-label\" style=\"text-align:left;\">" + "Battery Plugged In:" + "</td><td class=\"bldr2-text\" style=\"text-align:left;padding-left:5px\">" + statPluggedIn + "</td></tr>"
		String statCharging = (device.currentValue("EVBatteryCharging") == "true") ? "Yes" : "No"
		builder2 << "<tr><td class=\"bldr2-label\" style=\"text-align:left;\">" + "Battery Charging:" + "</td><td class=\"bldr2-text\" style=\"text-align:left;padding-left:5px\">" + statCharging + "</td></tr>"
		String statEVRange = device.currentValue("EVRange")
		builder2 << "<tr><td class=\"bldr2-label\" style=\"text-align:left;\">" + " EV Range:" + "</td><td class=\"bldr2-text\" style=\"text-align:left;padding-left:5px\">" + statEVRange + " miles</td></tr>"
		builder2 << "</table>"
		String EVHtml = builder2.toString()
		sendEvent(name:"EVstatusHtml", value: EVHtml)
	}
}

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
	data = "-- ${device.getDisplayName()} -- ${data ?: ''}"

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
				log.error("-- ${device.getDisplayName()} -- Invalid Log Setting")
		}
	}
}

