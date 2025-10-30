/**
 *  Hyundai Bluelink Application
 *
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
 *  9/17/21 - Add some events
 *  7/20/23 - Bug fix: authorization and token refresh stopped working
 *  5/2/25  - Improve debug logging, add attributes
 *  5/5/25  - Bug fix, add attributes, add some EV support
 *  5/8/25 - Refactoring to make more robust for missing JSON data - v1.04
 *  5/10/25 - Add EVBatteryCharging attribute
 *  10/18/25 - Fixed EV Start and Stop and added EVBatteryPluggedIn attribute (thx corinuss)
*
 *
 * Special thanks to:
 *
 * @thecloudtaylor for his excellent work on the Honeywell Home Thermostat App/Driver for Hubitat - his app was a template for this
 * App/Driver implementation.
 *
 * @Hacksore and team for their work on Bluelinky, the Node.js app that provided functional Bluelink API calls that I studied to implement this app. This team
 * reverse-engineered the undocumented Bluelink API. Awesome job.
 *
 * @corinuss for fixing EV Start/Stop
 *
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import org.json.JSONObject
import groovy.transform.Field

static String appVersion() { return "1.0.7-beta.climate.1" }
def setVersion() {
	if (state.version != appVersion())
	{
		// First install will be null, so don't request a refresh before they've set up.
		if (state.version != null) {
			log("Version updated from ${state.version} to ${appVersion()}.  Queued vehicle refresh request.", "info")
			state.needsVehicleRefresh = true
		}

		state.name = "Hyundai Bluelink Application"
		state.version = appVersion()
	}
}

@Field static String global_apiURL = "https://api.telematics.hyundaiusa.com"
@Field static String client_id = "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920"
@Field static String client_secret = "v558o935-6nne-423i-baa8"

// Chicken Switch
// Currently, classic profiles are not deleted when migrating, to allow for users to switch back to
// an older version of the app if there is a problem.  Eventually we'll want to set this to true to
// delete the settings after the next migration.
@Field static final DELETE_CLASSIC_CLIMATE_PROFILES = false

definition(
		name: "Hyundai Bluelink App",
		namespace: "tyuhl",
		author: "Tim Yuhl",
		description: "Application for Hyundai Bluelink web service access.",
		importUrl:"https://raw.githubusercontent.com/tyuhl/Hyundai-Bluelink/main/BluelinkApp.groovy",
		category: "Convenience",
		iconUrl: "",
		iconX2Url: ""
)

preferences {
	page(name: "mainPage")
	page(name: "accountInfoPage")
	page(name: "profilesPage")
	page(name: "profilesSavedPage")
	page(name: "debugPage", title: "Debug Options", install: false)
	page(name: "debugClimateCapabilitiesPage")
	page(name: "debugClimateCapabilitiesSavedPage")
}

def mainPage()
{
	dynamicPage(name: "mainPage", title: "Hyundai Bluelink App", install: true, uninstall: true) {
		section(getFormat("title","About Hyundai Bluelink Application")) {
			paragraph "This application and the corresponding driver are used to access the Hyundai Bluelink web services with Hubitat Elevation. Follow the steps below to configure the application."
		}
		section(getFormat("header-blue-grad","   1.  Set Bluelink Account Information")) {
		}
		getAccountLink()
		section(getFormat("item-light-grey","Account log-in")) {
			input(name: "stay_logged_in", type: "bool", title: "Stay logged in - turn off to force logging in before performing each action.", defaultValue: true, submitOnChange: true)
		}
		section(getFormat("header-blue-grad","   2.  Use This Button To Discover Vehicles and Create Drivers for Each")) {
			input 'discover', 'button', title: 'Discover Registered Vehicles', submitOnChange: true
		}
		listDiscoveredVehicles()
		section(getFormat("header-blue-grad","   3.  Review or Change Remote Start Options")) {
		}
		getProfileLink()
		section(getFormat("header-blue-grad","Change Logging Level")) {
			input name: "logging", type: "enum", title: "Log Level", description: "Debug logging", required: false, submitOnChange: true, defaultValue: "INFO", options: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
		}
		getDebugLink()
	}
}

def accountInfoPage()
{
	dynamicPage(name: "accountInfoPage", title: "<strong>Set Bluelink Account Information</strong>", install: false, uninstall: false) {
		section(getFormat("item-light-grey", "Username")) {
			input name: "user_name", type: "string", title: "Bluelink Username"
		}
		section(getFormat("item-light-grey", "Password")) {
			input name: "user_pwd", type: "password", title: "Bluelink Password"
		}
		section(getFormat("item-light-grey", "PIN")) {
			input name: "bluelink_pin", type: "password", title: "Bluelink PIN"
		}
	}
}

def getAccountLink() {
	section{
		href(
				name       : 'accountHref',
				title      : 'Account Information',
				page       : 'accountInfoPage',
				description: 'Set or Change Bluelink Account Information'
		)
	}
}

void cleanAppClimateProfileSettings(String profileName) {
	app.removeSetting("climate_${profileName}_airctrl")
	app.removeSetting("climate_${profileName}_airTemp")
	app.removeSetting("climate_${profileName}_defrost")
	app.removeSetting("climate_${profileName}_steeringHeat")
	app.removeSetting("climate_${profileName}_rearWindowHeat")
	app.removeSetting("climate_${profileName}_ignitionDur")

	// Clear out all seat location names that we support.
	CLIMATE_SEAT_LOCATIONS.each { k, locationInfo ->
		app.removeSetting("climate_${profileName}_${locationInfo.name}SeatHeatState")
	}
}

Map getSanitizedClimateProfileSettings(String profileName, Map climateProfiles, Map climateCapabilities)
{
	def profileSettings = [:]

	// Gather what the displayed settings defaults should be, according to what the user previously
	// saved or a reasonable default if they haven't set this setting yet.
	def climateProfile = climateProfiles?."${profileName}"

	profileSettings.airctrl = climateProfile?.airCtrl ?: true
	profileSettings.airTemp = climateProfile?.airTemp?.value ?: 70
	profileSettings.defrost = climateProfile?.defrost ?: false
	profileSettings.ignitionDur = climateProfile?.igniOnDuration ?: 10
	
	def heating1 = climateProfile?.heating1 ?: 0
	profileSettings.steeringHeat = heating1HasSteeringHeatingEnabled(heating1)
	profileSettings.rearWindowHeat = heating1HasRearWindowHeatingEnabled(heating1)
	
	profileSettings.seatHeatState = [:]

	CLIMATE_SEAT_LOCATIONS.each { seatId, locationInfo ->
			def current_value = 0
			def hasSeat = climateCapabilities.seatConfigs.containsKey(seatId)
			def seatConfig = hasSeat ? climateCapabilities.seatConfigs.seatId : null
			if (seatConfig != null) {
				current_value = climateProfile?.seatHeaterVentInfo?."${locationInfo.name}SeatHeatState"

				if (current_value == null || !seatConfig.supportedLevels.contains(current_value)) {
					current_value = getDefaultSeatLevel(seatConfig.supportedLevels)
				}
			}

			profileSettings.seatHeatState[seatId] = current_value
	}

	return profileSettings
}

def profilesPage() {
	// If the profiles haven't been migrated yet, do that now so we can show the user accurate data.
	migrateClassicProfiles()

	dynamicPage(name: "profilesPage", title: "<strong>Review/Edit Vehicle Start Options</strong>", nextPage: "profilesSavedPage", install: false, uninstall: false) {
		 section("Choose your vehicle:") {
		 	input(name: "climate_vehicle", type: "device.HyundaiBluelinkDriver", title: "Vehicle to configure", required: true, submitOnChange: true)
			paragraph "When done, click 'Next' at the bottom to save your climate profile changes to this vehicle."
		}

		if (climate_vehicle != null) {
			def childDevice = getChildDevice(climate_vehicle.deviceNetworkId)

			if (childDevice == null) {
				section("Error:") {
					paragraph "${climate_vehicle.getDisplayName()} does not appear to be a child device of this app.  Please delete the device and re-discover your vehicle through this app."
				}
			}
			else {
				// Identify what climate options are available to the user.
				def climateProfiles = childDevice.getClimateProfiles()
				def climateCapabilities = getSanitizedClimateCapabilities(childDevice)

				CLIMATE_PROFILES.each { profileName ->
					// Delete the current vehicle settings in the app so we can change their values.
					cleanAppClimateProfileSettings(profileName)

					def climateProfileSettings = getSanitizedClimateProfileSettings(profileName, climateProfiles, climateCapabilities)

					section(getFormat("header-blue-grad","Profile: ${profileName}")) {
						input(name: "climate_${profileName}_airctrl", type: "bool", title: "Turn on climate control when starting", defaultValue: climateProfileSettings.airctrl)
						input(name: "climate_${profileName}_airTemp", type: "number", title: "Climate temperature to set (${climateCapabilities.tempMin}-${climateCapabilities.tempMax})", defaultValue: climateProfileSettings.airTemp, range: "${climateCapabilities.tempMin}..${climateCapabilities.tempMax}", required: true)
						input(name: "climate_${profileName}_defrost", type: "bool", title: "Turn on Front Defroster when starting", defaultValue: climateProfileSettings.defrost)

						// Could customize this visibility on "rearWindowHeatCapable" and/or "sideMirrorHeatCapable", but they
						// currently share the same value, and pretty much every car has rear window heating.
						input(name: "climate_${profileName}_rearWindowHeat", type: "bool", title: "Turn on Rear Window and Side Mirror Defrosters when starting", defaultValue: climateProfileSettings.rearWindowHeat)

						if (climateCapabilities.steeringWheelHeatCapable) {
							input(name: "climate_${profileName}_steeringHeat", type: "bool", title: "Turn on Steering Wheel Heater when starting", defaultValue: climateProfileSettings.steeringHeat)
						}

						input(name: "climate_${profileName}_ignitionDur", type: "number", title: "Minutes run engine? (1-30)", defaultValue: climateProfileSettings.ignitionDur, range: "1..30", required: true)
					}

					if (!climateCapabilities.seatConfigs.isEmpty()) {
						// Collapse by default to match Bluelink app behavior and keep the page a bit tighter.
						section("Seat Temperatures", hideable:true, hidden: true) {
							climateCapabilities.seatConfigs.each { seatId, seatConfig ->
								input(
									name: "climate_${profileName}_${CLIMATE_SEAT_LOCATIONS[seatId].name}SeatHeatState",
									type: "enum",
									title: "${CLIMATE_SEAT_LOCATIONS[seatId].description} Temperature",
									defaultValue: climateProfileSettings.seatHeatState[seatId],
									options: seatConfig.supportedLevels.collect{ [ (it) : CLIMATE_SEAT_SETTINGS[it] ] },
									required: true)
							}
						}
					}
				}
			}
		}
	}
}

def profilesSavedPage() {
	dynamicPage(name: "profilesSavedPage", title: "<strong>Profiles saved</strong>", nextPage: "mainPage", install: false, uninstall: false) {
		if (climate_vehicle != null) {
			saveClimateProfiles()
			section("") {
				paragraph "Climate profiles have been saved to ${climate_vehicle.getDisplayName()}."
			}
		}
		else {
			section("") {
				paragraph "No climate profiles saved since no vehicle was selected."
			}
		}
	}
}

def saveClimateProfiles() {
	log("saveClimateProfiles called", "trace")

	if (climate_vehicle != null) {
	
		def childDevice = getChildDevice(climate_vehicle.deviceNetworkId)
		if (childDevice == null) {
			// This case shouldn't happen, because we already validated the child device earlier.
			log "Could not remap ${climate_vehicle.getDisplayName()} to childDevice to save climate profiles."
		}
		else {
			def climateCapabilities = getSanitizedClimateCapabilities(childDevice)

			def climateProfileStorage = [:]
			CLIMATE_PROFILES.each { profileName ->
				def climateProfile = [:]
				climateProfile.airCtrl = app.getSetting("climate_${profileName}_airctrl") ? 1: 0
				climateProfile.airTemp = ["unit" : 1, "value" : app.getSetting("climate_${profileName}_airTemp")]
				climateProfile.defrost = app.getSetting("climate_${profileName}_defrost")

				def rearWindowHeat = app.getSetting("climate_${profileName}_rearWindowHeat")
				def steeringHeat = climateCapabilities.steeringWheelHeatCapable ? app.getSetting("climate_${profileName}_steeringHeat") : false
				climateProfile.heating1 = getHeating1Value(rearWindowHeat, steeringHeat)

				climateProfile.igniOnDuration = app.getSetting("climate_${profileName}_ignitionDur")

				if (!climateCapabilities.seatConfigs.isEmpty())
				{
					climateProfile.seatHeaterVentInfo = [:]
					climateCapabilities.seatConfigs.each { seatId, seatConfig ->
						def shortSeatName = CLIMATE_SEAT_LOCATIONS[seatId].name

						// Even though we gave the input() options a list of maps [ int : string ],
						// it returns us the Integer as a String, so we need to convert it back.  :(
						def seatLevel = app.getSetting("climate_${profileName}_${shortSeatName}SeatHeatState") as Integer

						climateProfile.seatHeaterVentInfo["${shortSeatName}SeatHeatState"] = seatLevel
					}
				}

				climateProfileStorage[profileName] = climateProfile
			}

			childDevice.setClimateProfiles(climateProfileStorage)

			log("Saved climate profiles to ${climate_vehicle.getDisplayName()}: ${climateProfileStorage}", "debug")
		}
	}
}

def getProfileLink() {
	section{
		href(
				name       : 'profileHref',
				title      : 'Start Profiles',
				page       : 'profilesPage',
				description: 'View or edit vehicle start profiles'
		)
	}
}

////////
// Debug Stuff
///////
def getDebugLink() {
	section{
		href(
				name       : 'debugHref',
				title      : 'Debug buttons',
				page       : 'debugPage',
				description: 'Access debug buttons (refresh token, initialize)'
		)
	}
}

def debugPage() {
	dynamicPage(name:"debugPage", title: "Debug", install: false, uninstall: false) {
		section {
			paragraph "<strong>Debug buttons</strong>"
		}
		section {
			input 'refreshToken', 'button', title: 'Force Token Refresh', submitOnChange: true
		}
		section {
			input 'initialize', 'button', title: 'initialize', submitOnChange: true
		}
		getDebugClimateCapabilitiesLink()
	}
}

def getDebugClimateCapabilitiesLink() {
	section{
		href(
				name       : 'debugClimateCapabilitiesHref',
				title      : 'Modify Vehicle Climate Capabilities',
				page       : 'debugClimateCapabilitiesPage',
				description: 'Overried a vehicles auto-detected climate capabilities so App features not supported by the vehicle can be tested.'
		)
	}
}

def debugClimateCapabilitiesPage() {
	dynamicPage(name:"debugClimateCapabilitiesPage", title: "Override Climate Capabilities", nextPage: "debugClimateCapabilitiesSavedPage", install: false, uninstall: false) {
		section("Choose your vehicle:") {
			input(name: "climate_vehicle", type: "device.HyundaiBluelinkDriver", title: "Vehicle to configure", required: true, submitOnChange: true)
			paragraph "When done, click 'Next' at the bottom to save your changes to this vehicle."
			paragraph "Use the 'Force Refresh Vehicle Details' button on the Debug page to reset these values when done."
		}

		if (climate_vehicle != null) {
			def childDevice = getChildDevice(climate_vehicle.deviceNetworkId)

			if (childDevice == null) {
				section("Error:") {
					paragraph "${climate_vehicle.getDisplayName()} does not appear to be a child device of this app.  Please delete the device and re-discover your vehicle through this app."
				}
			}
			else {
				def climateCapabilities = getSanitizedClimateCapabilities(childDevice)
				log("climateCapabilities $climateCapabilities", "trace")

				app.removeSetting("vehicleClimateCapability_tempMin")
				app.removeSetting("vehicleClimateCapability_tempMax")
				app.removeSetting("vehicleClimateCapability_steeringWheelHeatCapable")

				// Clear out all seat location names that we support.
				CLIMATE_SEAT_LOCATIONS.each { seatId, locationInfo ->
					app.removeSetting("vehicleClimateCapability_seatConfigs_${seatId}_supportedLevels")
				}

				def current_tempMin = climateCapabilities.tempMin
				def current_tempMax = climateCapabilities.tempMax
				def current_steeringWheelHeatCapable = climateCapabilities.steeringWheelHeatCapable

				def current_seatConfigs = [:]
				CLIMATE_SEAT_LOCATIONS.each { seatId, locationInfo ->
					def seatConfig = [:]
					seatConfig.hasSeat = climateCapabilities.seatConfigs.containsKey(seatId)
					seatConfig.supportedLevels = seatConfig.hasSeat ? climateCapabilities.seatConfigs[seatId].supportedLevels : []
					current_seatConfigs[seatId] = seatConfig
				}

				section("") {
					input(name: "vehicleClimateCapability_tempMin", type: "number", title: "TempMin)", defaultValue: current_tempMin, required: true)
					input(name: "vehicleClimateCapability_tempMax", type: "number", title: "TempMax)", defaultValue: current_tempMax, required: true)
					input(name: "vehicleClimateCapability_steeringWheelHeatCapable", type: "bool", title: "Steering Wheel Heat Capable", defaultValue: current_steeringWheelHeatCapable)
				}

				section("Seat Configurations") {
					CLIMATE_SEAT_LOCATIONS.each { seatId, locationInfo ->
						log("current_seatConfigs[seatId].supportedLevels ${current_seatConfigs[seatId].supportedLevels}", "trace")
						input(
							name: "vehicleClimateCapability_seatConfigs_${seatId}_supportedLevels",
							type: "enum",
							title: "${CLIMATE_SEAT_LOCATIONS[seatId].description} Supported Levels",
							defaultValue: current_seatConfigs[seatId].supportedLevels,
							options: CLIMATE_SEAT_SETTINGS.collect{ settingId,name -> [(settingId) : name] },
							multiple: true)
					}
				}
			}
		}
	}
}

def debugClimateCapabilitiesSavedPage() {
	dynamicPage(name: "debugClimateCapabilitiesSavedPage", title: "<strong>Capabilities saved</strong>", nextPage: "mainPage", install: false, uninstall: false) {
		if (climate_vehicle != null) {
			saveClimateCapabilities()
			section("") {
				paragraph "Climate capabilities have been saved to ${climate_vehicle.getDisplayName()}."
			}
		}
		else {
			section("") {
				paragraph "No climate capabilities saved since no vehicle was selected."
			}
		}
	}
}

def saveClimateCapabilities() {
	log("saveClimateProfiles called", "trace")

	if (climate_vehicle != null) {
	
		def childDevice = getChildDevice(climate_vehicle.deviceNetworkId)
		if (childDevice == null) {
			// This case shouldn't happen, because we already validated the child device earlier.
			log "Could not remap ${climate_vehicle.getDisplayName()} to childDevice to save climate profiles."
		}
		else {
			def climateCapabilities = [:]

			climateCapabilities.tempMin = vehicleClimateCapability_tempMin
			climateCapabilities.tempMax = vehicleClimateCapability_tempMax
			climateCapabilities.steeringWheelHeatCapable = vehicleClimateCapability_steeringWheelHeatCapable

			def seatConfigs = [:]
			CLIMATE_SEAT_LOCATIONS.each { seatId, locationInfo ->
				def supportedLevels = app.getSetting("vehicleClimateCapability_seatConfigs_${seatId}_supportedLevels")
				def has_seat = (supportedLevels != null) && !supportedLevels.isEmpty()
				if (has_seat) {
					def seatInfo = [:]
					seatInfo.supportedLevels = supportedLevels.collect{ it as Integer }
					seatConfigs."$seatId" = seatInfo
				}
			}
			climateCapabilities.seatConfigs = seatConfigs

			childDevice.setClimateCapabilities(climateCapabilities)
			log("Saved climate capabilities to ${climate_vehicle.getDisplayName()}: ${climateCapabilities}", "debug")
		}
	}
}

def appButtonHandler(btn) {
	switch (btn) {
		case 'discover':
			authorize()
			getVehicles(true)
			break
		case 'refreshToken':
			refreshToken()
			break
		case 'initialize':
			initialize()
			break
		default:
			log("Invalid Button In Handler", "error")
	}
}

void installed() {
	log("Installed with settings: ${settings}", "trace")
	stay_logged_in = true // initialized to ensure token refresh happens with default setting
	initialize()
}

void updated() {
	log("Updated with settings: ${settings}", "trace")
	initialize()
}

void uninstalled() {
	log("Uninstalling Hyundai Bluelink App and deleting child devices", "info")
	unschedule()
	for (device in getChildDevices())
	{
		deleteChildDevice(device.deviceNetworkId)
	}
}

void initialize() {
	log("Initialize called", "trace")
	setVersion()
	unschedule()
	if(stay_logged_in && (state.refresh_token != null)) {
		refreshToken()
	}
}

void authorize() {
	log("authorize called", "trace")

	// Periodically ensure the app version has been updated, in case the user didn't click 'Done' in the App after an update.
	setVersion()

	// make sure there are no outstanding token refreshes scheduled
	unschedule()

	def headers = [
			"client_id": client_id,
			"client_secret": client_secret
	]
	def body = [
			"username": user_name,
			"password": user_pwd
	]
	def params = [uri: global_apiURL, path: "/v2/ac/oauth/token", headers: headers, body: body]

	try
	{
		httpPostJson(params) { response -> authResponse(response) }

		if (state.needsVehicleRefresh)
		{
			log("Refreshing vehicles details after authorize due to 'needsVehicleRefresh' being set.", "debug")
			getVehicles()
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		log("Login failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
	}
}

void refreshToken(Boolean refresh=false) {
	log("refreshToken called", "trace")

	// Periodically ensure the app version has been updated, in case the user didn't click 'Done' in the App after an update.
	setVersion()

	if (state.refresh_token != null)
	{
		def headers = [
				"client_id": client_id,
				"client_secret": client_secret
		]
		def body = [
				refresh_token: state.refresh_token
		]
		def params = [uri: global_apiURL, path: "/v2/ac/oauth/token/refresh", headers: headers, body: body]

		try
		{
			httpPostJson(params) { response -> authResponse(response) }

			if (state.needsVehicleRefresh)
			{
				log("Refreshing vehicles details after refreshToken to 'needsVehicleRefresh' being set.", "debug")
				getVehicles()
			}
		}
		catch (java.net.SocketTimeoutException e)
		{
			if (!refresh) {
				log("Socket timeout exception, will retry refresh token", "info")
				refreshToken(true)
			}
		}
		catch (groovyx.net.http.HttpResponseException e)
		{
			// could be authorization has been lost, try again after authorizing again
			if (!refresh) {
				log("Authoriztion may have been lost, will retry refreshing token after reauthorizing", "info")
				authorize()
				refreshToken(true)
			}
			else {
				log("refreshToken failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
			}
		}
	}
	else
	{
		log("Failed to refresh token, refresh token null.", "error")
	}
}

def authResponse(response)
{
	log("authResponse called", "trace")

	def reCode = response.getStatus()
	def reJson = response.getData()
	log("reCode: {$reCode}", "debug")
	log("reJson: {$reJson}", "debug")

	if (reCode == 200)
	{
		state.access_token = reJson.access_token
		state.refresh_token = reJson.refresh_token

		Integer expireTime = (Integer.parseInt(reJson.expires_in) - 180)
		log("Bluelink token refreshed successfully, Next Scheduled in: ${expireTime} sec", "debug")
		// set up token refresh
		if (stay_logged_in) {
			runIn(expireTime, refreshToken)
		}
	}
	else
	{
		log("LoginResponse Failed HTTP Request Status: ${reCode}", "error")
	}
}

def getVehicles(Boolean createNewVehicleDevices=false, Boolean retry=false)
{
	log("getVehicles called", "trace")

	def uri = global_apiURL + "/ac/v2/enrollment/details/" + user_name
	def headers = [ access_token: state.access_token, client_id: client_id, includeNonConnectedVehicles : "Y"]
	def params = [ uri: uri, headers: headers ]
	log("getVehicles ${params}", "debug")

	//add error checking
	LinkedHashMap reJson = []
	try
	{
		httpGet(params) { response ->
			def reCode = response.getStatus()
			reJson = response.getData()
			log("reCode: ${reCode}", "debug")
			logJsonHelper("getVehicles", reJson)
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		if (e.getStatusCode() == 401 && !retry)
		{
			log('Authorization token expired, will refresh and retry.', 'warn')
			refreshToken()
			getVehicles(createNewVehicleDevices, true)
		}
		log("getVehicles failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
		return
	}

	if (reJson.enrolledVehicleDetails == null) {
		log("No enrolled vehicles found.", "info")
	}
	else {
			reJson.enrolledVehicleDetails.each{ vehicle ->

				if (createNewVehicleDevices) {
					// Only log while creating new vehicles, so we don't spam during Refresh.
					log("Found vehicle: ${vehicle.vehicleDetails.nickName} with VIN: ${vehicle.vehicleDetails.vin}", "info")
				}

				// Try to get the device if it already exists.
				com.hubitat.app.ChildDeviceWrapper childDevice = getChildDevice(getChildDeviceNetId(vehicle.vehicleDetails.vin))
				if (childDevice == null && createNewVehicleDevices) {
					// Try to create a new device.
					childDevice = CreateChildDriver(vehicle.vehicleDetails.nickName, vehicle.vehicleDetails.vin)
				}

				if (childDevice != null) {
					//populate/update attributes
					safeSendEvent(childDevice, "NickName", vehicle.vehicleDetails.nickName)
					safeSendEvent(childDevice, "VIN", vehicle.vehicleDetails.vin)
					safeSendEvent(childDevice, "RegId", vehicle.vehicleDetails.regid)
					safeSendEvent(childDevice, "Odometer", vehicle.vehicleDetails.odometer)
					safeSendEvent(childDevice, "Model", vehicle.vehicleDetails.series)
					safeSendEvent(childDevice, "Trim", vehicle.vehicleDetails.trim)
					safeSendEvent(childDevice, "vehicleGeneration", vehicle.vehicleDetails.vehicleGeneration)
					safeSendEvent(childDevice, "brandIndicator", vehicle.vehicleDetails.brandIndicator)
					safeSendEvent(childDevice, "isEV", vehicle.vehicleDetails.evStatus == "E")  // ICE will be "N"

					cacheClimateCapabilities(childDevice, vehicle.vehicleDetails)
				 }
			}
	}

	// If a refresh was needed, we can clear out that flag now.
	state.remove("needsVehicleRefresh")
}

void getVehicleStatus(com.hubitat.app.DeviceWrapper device, Boolean refresh = false, Boolean retry=false)
{
	log("getVehicleStatus() called", "trace")

	if( !stay_logged_in ) {
		authorize()
	}

	//Note: this API can take up to a minute tor return if REFRESH=true because it contacts the car's modem and
	//doesn't use cached info.
	def uri = global_apiURL + "/ac/v2/rcs/rvs/vehicleStatus"
	def headers = getDefaultHeaders(device)
	headers.put('offset', '-5')
	headers.put('REFRESH', refresh.toString())
	int valTimeout = refresh ? 240 : 10 //timeout in sec.
	def params = [ uri: uri, headers: headers, timeout: valTimeout ]
	log("getVehicleStatus ${params}", "debug")

	//add error checking
	LinkedHashMap  reJson = []
	try
	{
		httpGet(params) { response ->
			def reCode = response.getStatus()
			reJson = response.getData()
			log("reCode: ${reCode}", "debug")
			logJsonHelper("getVehicleStatus", reJson)
		}

		// Update relevant device attributes
		safeSendEvent(device, 'Engine', reJson.vehicleStatus.engine, 'On', 'Off')
		safeSendEvent(device, 'DoorLocks', reJson.vehicleStatus.doorLock, 'Locked', 'Unlocked')
		safeSendEvent(device, 'Hood', reJson.vehicleStatus.hoodOpen, 'Open', 'Closed')
		safeSendEvent(device, 'Trunk', reJson.vehicleStatus.trunkOpen, 'Open', 'Closed')
		safeSendEvent(device, "Range", reJson.vehicleStatus.dte.value)
		safeSendEvent(device, "BatterySoC", reJson.vehicleStatus.battery.batSoc)
		safeSendEvent(device, "LastRefreshTime", reJson.vehicleStatus.dateTime)
		safeSendEvent(device, "TirePressureWarning", reJson.vehicleStatus.tirePressureLamp.tirePressureWarningLampAll, "true", "false")
		safeSendEvent(device, "Odometer", reJson.vehicleStatus.odometer)
		if (device.currentValue("isEV") == "true") {
			safeSendEvent(device, "EVBatteryCharging", reJson.vehicleStatus.evStatus.batteryCharge, "true", "false")
			safeSendEvent(device, "EVBatteryPluggedIn", reJson.vehicleStatus.evStatus.batteryPlugin, "true", "false")
			safeSendEvent(device, "EVBattery", reJson.vehicleStatus.evStatus.batteryStatus)
			safeSendEvent(device, "EVRange", reJson.vehicleStatus.evStatus.drvDistance[0].rangeByFuel.evModeRange.value)
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		if (e.getStatusCode() == 401 && !retry)
		{
			log('Authorization token expired, will refresh and retry.', 'warn')
			refreshToken()
			getVehicleStatus(device, refresh, true)
		}
		log("getVehicleStatus failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
	}
}

void getLocation(com.hubitat.app.DeviceWrapper device, Boolean refresh=false)
{
	log("getLocation() called", "trace")

	if( !stay_logged_in ) {
		authorize()
	}

	def uri = global_apiURL + '/ac/v2/rcs/rfc/findMyCar'
	def headers = getDefaultHeaders(device)
	headers.put('offset', '-5')
	def params = [ uri: uri, headers: headers, timeout: 120 ] // long timeout, contacts modem
	log("getLocation ${params}", "debug")

	LinkedHashMap reJson = []
	try
	{
		httpGet(params) { response ->
			int reCode = response.getStatus()
			reJson = response.getData()
			log("reCode: ${reCode}", "debug")
			logJsonHelper("getLocation", reJson)
			if (reCode == 200) {
				log("getLocation successful.","info")
				sendEventHelper(device, "Location", true)
			}
			if( reJson.coord != null) {
				//convert altitude from m to ft
				def theAlt = reJson.coord.alt * 3.28084
				safeSendEvent(device, 'locLatitude', reJson.coord.lat)
				safeSendEvent(device, 'locLongitude', reJson.coord.lon)
				safeSendEvent(device, 'locUpdateTime', reJson.time)
			}
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		if (e.getStatusCode() == 401 && !retry)
		{
			log('Authorization token expired, will refresh and retry.', 'warn')
			refreshToken()
			getLocation(device, true)
			return
		}
		log("getLocation failed -- ${e.getLocalizedMessage()}: Status: ${e.response.getStatus()}", "error")
		sendEventHelper(device, "Location", false)
	}
}

void Lock(com.hubitat.app.DeviceWrapper device)
{
	if( !LockUnlockHelper(device, '/ac/v2/rcs/rdo/off') )
	{
		log("Lock call failed -- try waiting before retrying", "info")
		sendEventHelper(device, "Lock", false)
	} else
	{
		log("Lock call made to car -- can take some time to lock", "info")
		sendEventHelper(device, "Lock", true)
	}
}

void Unlock(com.hubitat.app.DeviceWrapper device)
{
	if( !LockUnlockHelper(device, '/ac/v2/rcs/rdo/on') )
	{
		log("Unlock call failed -- try waiting before retrying", "info")
		sendEventHelper(device, "Unlock", false)
	}else
	{
		log("Unlock call made to car -- can take some time to unock", "info")
		sendEventHelper(device, "Unlock", true)
	}
}

void Start(com.hubitat.app.DeviceWrapper device, String profile, Boolean retry=false)
{
	log("Start() called with profile: ${profile}", "trace")

	if( !stay_logged_in ) {
		authorize()
	}

	def isEV = device.currentValue("isEV") == "true"
	def uri = global_apiURL + (isEV ? '/ac/v2/evc/fatc/start' : '/ac/v2/rcs/rsc/start')
	def headers = getDefaultHeaders(device)
	headers.put('offset', '-4')

	// If the classic profiles haven't been migrated yet, do that now so we can apply accurate data.
	migrateClassicProfiles()
	
	// Fill in profile parameters
	def childDevice = getChildDevice(device.deviceNetworkId)
	def climateBody = [ "airCtrl" : 0 ] // default to off unless we have data
	if (childDevice == null) {
		log("Could not obtain climate profiles.  ${device.getDisplayName()} does not appear to be a child device of this app.  Please delete the device and re-discover your vehicle through this app.", "error")
	}
	else {
		def climateProfiles = childDevice.getClimateProfiles()
		if (climateProfiles == null || !climateProfiles.containsKey(profile)) {
			// Empty should always use defaults without complaint
			if (!profile.isEmpty()) {
				log("Ignoring profile '$profile' when starting vehicle ${device.getDisplayName()} because it doesn't exist.", "warn")
			}
		}
		else {
			climateBody = climateProfiles[profile]
		}
	}

	String theVIN = device.currentValue("VIN")
	String theCar = device.currentValue("NickName")
	def body = [
			"username": user_name,
			"vin": theVIN,
			"Ims": 0
	] + climateBody
	String sBody = JsonOutput.toJson(body).toString()

	def params = [ uri: uri, headers: headers, body: sBody, timeout: 10 ]
	log("Start ${params}", "debug")

	int reCode = 0
	try
	{
		httpPostJson(params) { response ->
			reCode = response.getStatus()
			if (reCode == 200) {
				log("Vehicle ${theCar} successfully started.","info")
				sendEventHelper(device, "Start", true)
			}
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		if (e.getStatusCode() == 401 && !retry)
		{
			log('Authorization token expired, will refresh and retry.', 'warn')
			refreshToken()
			Start(device, profile,true)
			return
		}
		log("Start vehicle failed -- ${e.getLocalizedMessage()}: Status: ${e.response.getStatus()}", "error")
		sendEventHelper(device, "Start", false)
	}
}

void Stop(com.hubitat.app.DeviceWrapper device, Boolean retry=false)
{
	log("Stop() called", "trace")

	if( !stay_logged_in ) {
		authorize()
	}

	def isEV = device.currentValue("isEV") == "true"
	def uri = global_apiURL + (isEV ? '/ac/v2/evc/fatc/stop' : '/ac/v2/rcs/rsc/stop')
	def headers = getDefaultHeaders(device)
	headers.put('offset', '-4')
	def params = [ uri: uri, headers: headers, timeout: 10 ]
	log("Stop ${params}", "debug")

	String theCar = device.currentValue("NickName")
	int reCode = 0
	try
	{
		httpPost(params) { response ->
			reCode = response.getStatus()
			if (reCode == 200) {
				log("Vehicle ${theCar} successfully stopped.","info")
				sendEventHelper(device, "Stop", true)
			}
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		if (e.getStatusCode() == 401 && !retry)
		{
			log('Authorization token expired, will refresh and retry.', 'warn')
			refreshToken()
			Stop(device, true)
			return
		}
		log("Stop vehicle failed -- ${e.getLocalizedMessage()}: Status: ${e.response.getStatus()}", "error")
		sendEventHelper(device, "Stop", false)
	}
}

///
// Climate functionality
///
@Field static final CLIMATE_TEMP_MIN_DEFAULT = 62
@Field static final CLIMATE_TEMP_MAX_DEFAULT = 82

@Field static final CLIMATE_PROFILES =
[
	"Summer",
	"Winter",
	"Profile3"
]

@Field static final CLIMATE_SEAT_LOCATIONS =
[
	"1" : ["name" : "drv", "description" : "Driver Seat" ],
	"2" : ["name" : "ast", "description" : "Passenger Seat" ],
	"3" : ["name" : "rl",  "description" : "Rear Left Seat" ],
	"4" : ["name" : "rr",  "description" : "Rear Right Seat" ],
	// Protection against newer locations.  These seats will probably end up ignored.
].withDefault { otherValue -> ["name" : "Unknown$otherValue", "description" : "Unknown$otherValue Seat" ]  }

@Field static final CLIMATE_SEAT_SETTINGS =
[
	0 : "Off",
	1 : "On",
	2 : "Off",
	3 : "Cool Low",
	4 : "Cool Medium",
	5 : "Cool High",
	6 : "Heat Low",
	7 : "Heat Medium",
	8 : "Heat High",
	// Protection against newer settings.  This should continue to function even with Unknowns.
].withDefault { otherValue -> "Unknown$otherValue" }

// heating1 values are:
// ====================
// 0: 'Off',
// 1: 'Steering Wheel and Rear Window',
// 2: 'Rear Window',
// 3: 'Steering Wheel',
// 4: "Steering Wheel and Rear Window" // # Seems to be the same as 1 but different region (EU)
Integer getHeating1Value(Boolean enableRearWindowHeat, Boolean enableSteeringHeat) {
	if (enableRearWindowHeat) {
		// If supporting EU, might need to return 4 here instead of 1.
		return enableSteeringHeat ? 1 : 2
	}
	else {
		return enableSteeringHeat ? 3 : 0
	}
}

Boolean heating1HasRearWindowHeatingEnabled(Integer heating1) {
	return (heating1 == 1 || heating1 == 2 || heating1 == 4)
}

Boolean heating1HasSteeringHeatingEnabled(Integer heating1) {
	return (heating1 == 1 || heating1 == 3 || heating1 == 4)
}

Integer getDefaultSeatLevel(ArrayList supportedLevels) {
	// There are multiple 'Off' states ('0' and '2').  '0' should be allowed for all vehicle types,
	// but prefer the supported 'Off' state whenever possible.
	Integer defaultLevel = 0
	if (supportedLevels.contains(2)) {
		defaultLevel = 2
	}

	return defaultLevel
}

// Converts raw climate seat capabilities from Bluelink to what we store in the device.
// (Filters out data we don't care about, and does some upfront processing on some strings.)
Map sanitizeSeatConfigs(ArrayList seatConfigs) {
	def sanitizedSeatConfigs = [:]

	seatConfigs?.each{ seatConfig ->
		if (seatConfig.seatLocationID == null) {
			log("Seat location doesn't have a locationID", "debug")
		}
		else if (!CLIMATE_SEAT_LOCATIONS.containsKey(seatConfig.seatLocationID)) {
			log("Seat location ${seatConfig.seatLocationID} is not recognized and will be ignored.  Contact developer to add support for this seat.", "info")
		}
		else {
			def supportedLevelsString = seatConfig.supportedLevels ?: "0"

			// This is a comma-delimited string, which isn't that useful to us.
			// Convert it to an integer list before saving, which is much easier to work with.
			sanitizedSeatConfigs[seatConfig.seatLocationID] = [
				"supportedLevels" : supportedLevelsString.split(',').collect{ it as Integer }
			]
		}
	}

	return sanitizedSeatConfigs
}

// Cache the vehicle's climate capabilities to the device.
void cacheClimateCapabilities(com.hubitat.app.DeviceWrapper device, Map vehicleDetails)
{
	def climateCapabilities = [
		"tempMin" : vehicleDetails.additionalVehicleDetails?.minTemp ?: CLIMATE_TEMP_MIN_DEFAULT,
		"tempMax" : vehicleDetails.additionalVehicleDetails?.maxTemp ?: CLIMATE_TEMP_MAX_DEFAULT,
		"steeringWheelHeatCapable" : (vehicleDetails.steeringWheelHeatCapable ?: "NO") == "YES",
		"seatConfigs" : sanitizeSeatConfigs(vehicleDetails.seatConfigurations?.seatConfigs)
	]

	// Need to convert to a child device to be able to save to the device.
	def childDevice = getChildDevice(device.deviceNetworkId)
	if (childDevice == null) {
		 log("Could not cache climate capabilities.  ${device.getDisplayName()} does not appear to be a child device of this app.  Please delete the device and re-discover your vehicle through this app.", "error")
	}
	else {
		childDevice.setClimateCapabilities(climateCapabilities)
	}
}

// Gets the vehicle's climate capabilities cached from the device and handles missing data.
Map getSanitizedClimateCapabilities(com.hubitat.app.ChildDeviceWrapper device)
{
	Map vehicleDetails= device.getClimateCapabilities()

	if (vehicleDetails == null) {
		vehicleDetails = [:]
	}

	if (!vehicleDetails.containsKey("tempMin")){
		vehicleDetails.tempMin = CLIMATE_TEMP_MIN_DEFAULT
	}

	if (!vehicleDetails.containsKey("tempMax")){
		vehicleDetails.tempMax = CLIMATE_TEMP_MAX_DEFAULT
	}

	if (!vehicleDetails.containsKey("steeringWheelHeatCapable")){
		vehicleDetails.steeringWheelHeatCapable = false
	}

	if (!vehicleDetails.containsKey("seatConfigs")){
		vehicleDetails.seatConfigs = [:]
	}

	return vehicleDetails
}

// Migrates climate profiles exactly from the previous version, despite what the vehicle actually supports.
// This will continue to work as it did before, and features will be add or removed according to vehicle
// capabilities the next time the user modifies the profile for their vehicle.
void migrateClassicProfiles() {
	// Check if one setting exists before doing the full migration.
	// If we've already cleaned it up, the data has already been migrated.
	if (app.getSetting("Summer_climate") != null) {
		log("Attempting to migrateClassicProfiles", "trace")

		def climateProfileStorage = [:]
		CLIMATE_PROFILES.each { profileName ->
			def climateProfile = [:]
			climateProfile.airCtrl = app.getSetting("${profileName}_climate") ? 1: 0
			climateProfile.defrost = app.getSetting("${profileName}_defrost")
			climateProfile.heating1 = app.getSetting("${profileName}_heatAcc") ? 1 : 0
			climateProfile.igniOnDuration = app.getSetting("${profileName}_ignitionDur")

			def temp_setting = app.getSetting("${profileName}_temp")
			if (temp_setting == "LO") {
				temp_setting = CLIMATE_TEMP_MIN_DEFAULT
			}
			else if (temp_setting == "HI") {
				temp_setting = CLIMATE_TEMP_MAX_DEFAULT
			}
			climateProfile.airTemp = ["unit" : 1, "value" : temp_setting]

			climateProfileStorage[profileName] = climateProfile
		}

		getChildDevices().each { device ->
			if (device.getClimateProfiles() == null) {
				log("Migrated climate profile to ${device.getDisplayName()}", "info")
				device.setClimateProfiles(climateProfileStorage)
			}
		}

		if (DELETE_CLASSIC_CLIMATE_PROFILES) {
			log("Deleting classic climate profiles.", "debug")

			// Clean up deprected profiles.
			CLIMATE_PROFILES.each { profileName ->
				app.removeSetting("${profileName}_climate")
				app.removeSetting("${profileName}_temp")
				app.removeSetting("${profileName}_defrost")
				app.removeSetting("${profileName}_heatAcc")
				app.removeSetting("${profileName}_ignitionDur")
			}
		}
	}
}

///
// Supporting helpers
///
private void sendEventHelper(com.hubitat.app.DeviceWrapper device, String sentCommand, Boolean result)
{
	log("sendEventHelper() called", "trace")
	String strResult = result ? "successfully sent to vehicle" : "sent to vehicle - error returned"
	String strDesc = "Command ${sentCommand} ${strResult}"
	String strVal = result ? "Successful" : "Error"
	sendEvent(device, [name: sentCommand, value: strVal, descriptionText: strDesc, isStateChange: true])
}

private Boolean LockUnlockHelper(com.hubitat.app.DeviceWrapper device, String urlSuffix, Boolean retry=false)
{
	log("LockUnlockHelper() called", "trace")

	if( !stay_logged_in ) {
		authorize()
	}

	def uri = global_apiURL + urlSuffix
	def headers = getDefaultHeaders(device)
	headers.put('offset', '-5')
	String theVIN = device.currentValue("VIN")
	def body = [
			"userName": user_name,
			"vin": theVIN
	]

	def params = [ uri: uri, headers: headers, body: body, timeout: 10 ]
	log("LockUnlockHelper ${params}", "debug")

	int reCode = 0
	try
	{
		httpPost(params) { response ->
			reCode = response.getStatus()
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		if (e.getStatusCode() == 401 && !retry)
		{
			log('Authorization token expired, will refresh and retry.', 'warn')
			refreshToken()
			LockUnlockHelper(device, urlSuffix, true)
		}
		log("LockUnlockHelper failed -- ${e.getLocalizedMessage()}: Status: ${e.response.getStatus()}", "error")
	}
	return (reCode == 200)
}

private void listDiscoveredVehicles() {
	def children = getChildDevices()
	def builder = new StringBuilder()
	builder << "<ul>"
	children.each {
		if (it != null) {
			builder << "<li><a href='/device/edit/${it.getId()}'>${it.getDisplayName()}</a></li>"
		}
	}
	builder << "</ul>"
	def theCars = builder.toString()
	if (!children.isEmpty())
	{
		section {
			paragraph "Discovered vehicles are listed below:"
			paragraph theCars
		}
	}
}


private LinkedHashMap<String, String> getDefaultHeaders(com.hubitat.app.DeviceWrapper device) {
	log("getDefaultHeaders() called", "trace")

	LinkedHashMap<String, String> theHeaders = []
	try {
		String theVIN = device.currentValue("VIN")
		String regId = device.currentValue("RegId")
		String generation = device.currentValue("vehicleGeneration")
		String brand = device.currentValue("brandIndicator")
		theHeaders = [
				'access_token' : state.access_token,
				'client_id'   : client_id,
				'language'    : '0',
				'vin'         : theVIN,
				'APPCLOUD-VIN' : theVIN,
				'username' : user_name,
				'registrationId' : regId,
				'gen' : generation,
				'to' : 'ISS',
				'from' : 'SPA',
				'encryptFlag' : 'false',
				'bluelinkservicepin' : bluelink_pin,
				'brandindicator' : brand
		]
	} catch(Exception e) {
		log("Unable to generate API headers - Did you fill in all required information?", "error")
	}

	return theHeaders
}

private getChildDeviceNetId(String Vin)
{
	return "Hyundai_" + Vin
}

private com.hubitat.app.ChildDeviceWrapper CreateChildDriver(String Name, String Vin)
{
	log("CreateChildDriver called", "trace")
	String vehicleNetId = getChildDeviceNetId(Vin)
	com.hubitat.app.ChildDeviceWrapper newDevice = null
	try {
			newDevice = addChildDevice(
				'tyuhl',
				'Hyundai Bluelink Driver',
				vehicleNetId,
				[
						name : "Hyundai Bluelink Driver",
						label: Name
				])
	}
	catch (com.hubitat.app.exception.UnknownDeviceTypeException e) {
		log("${e.message} - you need to install the appropriate driver.", "info")
	}
	catch (IllegalArgumentException e) {
		//Intentionally ignored.  Expected if device id already exists in HE.
		log("Ignored: ${e.message}", "trace")
	}
	return newDevice
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
	data = "-- ${app.label} -- ${data ?: ''}"

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
				log.error("-- ${app.label} -- Invalid Log Setting")
		}
	}
}

// concept stolen bptworld, who stole from @Stephack Code
def getFormat(type, myText="") {
	if(type == "header-green") return "<div style='color:#ffffff; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background-color:#81BC00; border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
	if(type == "header-light-grey") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background-color:#D8D8D8; border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
	if(type == "header-blue-grad") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; line-height: 2.0; font-weight: bold; padding-left: 10px; background: linear-gradient(to bottom, #d4e4ef 0%,#86aecc 100%);  border: 2px'>${myText}</div>"
	if(type == "header-center-blue-grad") return "<div style='text-align:center; color:#000000; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background: linear-gradient(to bottom, #d4e4ef 0%,#86aecc 100%);  border: 2px'>${myText}</div>"
	if(type == "item-light-grey") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; font-weight: normal; padding-left: 10px; background-color:#D8D8D8; border: 1px solid'>${myText}</div>"
	if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}

private void logJsonHelper(String api_call, LinkedHashMap input)
{
	if (determineLogLevel("DEBUG") >= determineLogLevel(settings?.logging ?: "TRACE")){
		String strJson = JsonOutput.prettyPrint(new JSONObject(input).toString())
		log("${api_call} - reJson: ${strJson}", "debug")
	}
}

private void safeSendEvent(com.hubitat.app.DeviceWrapper device, String attrib, def val, def valTrue = null, def valFalse = null)
{
	if (val == null) {
		log(" *** Attribute: ${attrib} JSON value is null", "debug")
	}
	else {
		if (valTrue && valFalse) 
		{
			sendEvent(device, [name: attrib, value: val ? valTrue : valFalse])
		} 
		else if ((valTrue == null) && (valFalse == null)) 
		{
			sendEvent(device, [name: attrib, value: val])
		}
		else
		{
			log("SafeSendEvent programming error - missing argument value", "error")
		}
	}
}
