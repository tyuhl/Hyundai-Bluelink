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
 *
 *
 * Special thanks to:
 *
 * @thecloudtaylor for his excellent work on the Honeywell Home Thermostat App/Driver for Hubitat - his app was a template for this
 * App/Driver implementation.
 *
 * @Hacksore and team for their work on Bluelinky, the Node.js app that provided functional Bluelink API calls that I studied to implement this app. This team
 * reverse-engineered the undocumented Bluelink API. Awesome job.
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

static String appVersion()   { return "1.0.1" }
def setVersion(){
	state.name = "Hyundai Bluelink Application"
	state.version = "1.0.1"
}

@Field static String global_apiURL = "https://api.telematics.hyundaiusa.com"
@Field static String client_id = "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920"
@Field static String client_secret = "v558o935-6nne-423i-baa8"

definition(
		name: "Bluelink",
		namespace: "coltonton",
		author: "Tim Yuhl & Coltonton",
		description: "Application for Hyundai Bluelink web service access.",
		importUrl:"https://raw.githubusercontent.com/coltonton/Hyundai-Bluelink/main/BluelinkApp.groovy",
		category: "Convenience",
		iconUrl: "",
		iconX2Url: ""
)

preferences {
	page(name: "mainPage")
	page(name: "accountInfoPage")
	page(name: "profilesPage")
	page(name: "debugPage", title: "Debug Options", install: false)
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
			input name: "user_pwd", type: "string", title: "Bluelink Password"
		}
		section(getFormat("item-light-grey", "PIN")) {
			input name: "bluelink_pin", type: "string", title: "Bluelink PIN"
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

def profilesPage()
{
	dynamicPage(name: "profilesPage", title: "<strong>Review/Edit Vehicle Start Options</strong>", install: false, uninstall: false) {
		for (int i = 0; i < 3; i++) {
			String profileName = "Summer"
			switch(i)
			{
				case 0: profileName = "Summer"
					break
				case 1: profileName = "Winter"
					break
				case 2: profileName = "JustStart"
			}
			def tempOptions = ["LO", "64", "66", "68", "70", "72", "74", "76", "78", "80", "HI"]
			section(getFormat("header-blue-grad","Profile: ${profileName}")) {
				input(name: "${profileName}_climate", type: "bool", title: "Turn on climate control when starting", defaultValue: true, submitOnChange: true)
				input(name: "${profileName}_temp", type: "enum", title: "Climate temperature to set", options: tempOptions, defaultValue: "70", required: true)
				input(name: "${profileName}_defrost", type: "bool", title: "Turn on defrost when starting", defaultValue: false, submitOnChange: true)
				input(name: "${profileName}_heatAcc", type: "bool", title: "Turn on heated accessories when starting", defaultValue: false, submitOnChange: true)
				input(name: "${profileName}_ignitionDur", type: "number", title: "Minutes run engine? (1-10)", defaultValue: 10, range: "1..10", required: true, submitOnChange: true)
			}
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
	}
}

def appButtonHandler(btn) {
	switch (btn) {
		case 'discover':
			authorize()
			getVehicles()
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
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		log("Login failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
	}
}

void refreshToken(Boolean refresh=false) {
	log("refreshToken called", "trace")

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

def getVehicles(Boolean retry=false)
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
			log("reJson: ${reJson}", "debug")
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		if (e.getStatusCode() == 401 && !retry)
		{
			log('Authorization token expired, will refresh and retry.', 'warn')
			refreshToken()
			getVehicles(true)
		}
		log("getVehicles failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
		return
	}

	if (reJson.enrolledVehicleDetails == null) {
		log("No enrolled vehicles found.", "info")
	}
	else {
			reJson.enrolledVehicleDetails.each{ vehicle ->
				log("Found vehicle: ${vehicle.vehicleDetails.nickName} with VIN: ${vehicle.vehicleDetails.vin}", "info")
				def newDevice = CreateChildDriver(vehicle.vehicleDetails.nickName, vehicle.vehicleDetails.vin)
				if (newDevice != null) {
					//populate attributes
					sendEvent(newDevice, [name: "Color", value:  vehicle.vehicleDetails.color])
					sendEvent(newDevice, [name: "Year", value:  vehicle.vehicleDetails.modelYear])
					sendEvent(newDevice, [name: "brandIndicator", value:  vehicle.vehicleDetails.brandIndicator])
					if (vehicle.vehicleDetails.brandIndicator == "H") {
						sendEvent(newDevice, [name: "Make", value:  "Hyundai"])
					}
					else if (vehicle.vehicleDetails.brandIndicator == "K") {
						sendEvent(newDevice, [name: "Make", value:  "Kia"])
					}
					else if (vehicle.vehicleDetails.brandIndicator == "G") {
						sendEvent(newDevice, [name: "Make", value:  "Genisis"])
					}
					sendEvent(newDevice, [name: "Model", value:  vehicle.vehicleDetails.series])
					sendEvent(newDevice, [name: "Trim", value:  vehicle.vehicleDetails.trim])
					if (vehicle.vehicleDetails.evStatus == "Y") {
						sendEvent(newDevice, [name: "IsEV", value:  "True"])
					}
					else if (vehicle.vehicleDetails.evStatus == "N") {
						sendEvent(newDevice, [name: "Vehicle.TransmissionType", value:  vehicle.vehicleDetails.transmissiontype])
					}
					sendEvent(newDevice, [name: "HMA-Model", value:  vehicle.vehicleDetails.hmaModel])
					sendEvent(newDevice, [name: "vehicleGeneration", value:  vehicle.vehicleDetails.vehicleGeneration])
					
					sendEvent(newDevice, [name: "Nickname", value:  vehicle.vehicleDetails.nickName])
					sendEvent(newDevice, [name: "VIN", value:  vehicle.vehicleDetails.vin])
					sendEvent(newDevice, [name: "Odometer", value:  vehicle.vehicleDetails.odometer])
					odud = vehicle.vehicleDetails.odometerUpdateDate
					sendEvent(newDevice, [name: "OdometerUpdateDate", value:  odud[4..5]+"/"+odud[6..7]+"/"+odud[0..3]])
					sendEvent(newDevice, [name: "ModemType", value:  vehicle.vehicleDetails.vehicleModemType])
					sendEvent(newDevice, [name: "RegId", value:  vehicle.vehicleDetails.regid])
				 }
			}
	}
}

void updateVehicleOdometer(com.hubitat.app.DeviceWrapper device, Boolean retry=false) {
	log("updateVehicleOdometer called", "trace")

	if( !stay_logged_in ) {
		authorize()
	}

	def uri = global_apiURL + "/ac/v2/enrollment/details/" + user_name
	def headers = [ access_token: state.access_token, client_id: client_id, includeNonConnectedVehicles : "Y"]
	def params = [ uri: uri, headers: headers ]
	log("updateVehicleOdometer ${params}", "debug")

	//add error checking
	LinkedHashMap  reJson = []
	try
	{
		httpGet(params) { response ->
			def reCode = response.getStatus()
			reJson = response.getData()
			log("reCode: ${reCode}", "debug")
			log("reJson: ${reJson}", "debug")
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		if (e.getStatusCode() == 401 && !retry)
		{
			log('Authorization token expired, will refresh and retry.', 'warn')
			refreshToken()
			updateVehicleOdometer(device, true)
		}
		log("updateVehicleOdometer failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
		return
	}

	if (reJson.enrolledVehicleDetails == null) {
		log("No vehicles found to read odometer.", "info")
	}
	else {
		String theVIN = device.currentValue("VIN")
		reJson.enrolledVehicleDetails.each{ vehicle ->
				if(vehicle.vehicleDetails.vin == theVIN) {
					sendEvent(device, [name: "Odometer", value:  vehicle.vehicleDetails.odometer])
			}
		}
	}
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
			log("reJson: ${reJson}", "debug")
		}
		// Update relevant device attributes
		sendEvent(device, [name: 'Engine', value: reJson.vehicleStatus.engine ? 'On' : 'Off'])
		sendEvent(device, [name: 'DoorLocks', value: reJson.vehicleStatus.doorLock ? 'Locked' : 'Unlocked'])
		sendEvent(device, [name: 'Hood', value: reJson.vehicleStatus.hoodOpen ? 'Open' : 'Closed'])
		sendEvent(device, [name: 'Trunk', value: reJson.vehicleStatus.trunkOpen ? 'Open' : 'Closed'])
		sendEvent(device, [name: "LastRefreshTime", value: reJson.vehicleStatus.dateTime])

		sendEvent(device, [name: 'LowOilLevel', value: reJson.vehicleStatus.engineOilStatus ? 'True' : 'False'])
		sendEvent(device, [name: 'LowWasherFluid', value: reJson.vehicleStatus.washerFluidStatus ? 'True' : 'False'])
		sendEvent(device, [name: 'Range', value: reJson.vehicleStatus.dte.value])
		sendEvent(device, [name: 'FuelLevel', value: reJson.vehicleStatus.fuelLevel+"%"])
		sendEvent(device, [name: 'LowFuel', value: reJson.vehicleStatus.lowFuelLight ? 'True' : 'False'])
		sendEvent(device, [name: 'LowBrakeFluid', value: reJson.vehicleStatus.breakOilStatus ? 'True' : 'False'])
		sendEvent(device, [name: 'FL-TPMS', value: reJson.vehicleStatus.tirePressureLamp.tirePressureWarningLampFrontLeft ? 'Low' : 'NoWarning'])
		sendEvent(device, [name: 'FR-TPMS', value: reJson.vehicleStatus.tirePressureLamp.tirePressureWarningLampFrontRight ? 'Low' : 'NoWarning'])
		sendEvent(device, [name: 'RL-TPMS', value: reJson.vehicleStatus.tirePressureLamp.tirePressureWarningLampRearLeft ? 'Low' : 'NoWarning'])
		sendEvent(device, [name: 'RR-TPMS', value: reJson.vehicleStatus.tirePressureLamp.tirePressureWarningLampRearRight ? 'Low' : 'NoWarning'])
		sendEvent(device, [name: 'KeyBattery', value: reJson.vehicleStatus.smartKeyBatteryWarning ? 'Low Battery!' : 'Functional'])
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
			log("reJson: ${reJson}", "debug")
			if (reCode == 200) {
				log("getLocation successful.","info")
				sendEventHelper(device, "Location", true)
			}
			if( reJson.coord != null) {
				sendEvent(device, [name: 'locLatitude', value: reJson.coord.lat])
				sendEvent(device, [name: 'locLongitude', value: reJson.coord.lon])
				sendEvent(device, [name: 'locAltitude', value: reJson.coord.alt])
				sendEvent(device, [name: 'locSpeed', value: reJson.speed.value])
				sendEvent(device, [name: 'locUpdateTime', value: reJson.time])
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

	def uri = global_apiURL + '/ac/v2/rcs/rsc/start'
	def headers = getDefaultHeaders(device)
	headers.put('offset', '-4')

	// Fill in profile parameters
	int climateCtrl = settings["${profile}_climate"] ? 1: 0   // 1: climate on, 0: climate off
	int heatedAcc = settings["${profile}_heatAcc"] ? 1: 0     // 1: heated steering on, seats?
	String Temp = settings["${profile}_temp"]
	Boolean Defrost = settings["${profile}_defrost"]
	int Duration = settings["${profile}_ignitionDur"]

	String theVIN = device.currentValue("VIN")
	String theCar = device.currentValue("NickName")
	def body = [
			"username": user_name,
			"vin": theVIN,
			"Ims": 0,
			"airCtrl" : climateCtrl,
			"airTemp" : ["unit" : 1, "value": Temp],
			"defrost" : Defrost,
			"heating1" : heatedAcc,
			"igniOnDuration" : Duration,
			"seatHeaterVentInfo" : null  //what this does is unknown
	]
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

	def uri = global_apiURL + '/ac/v2/rcs/rsc/stop'
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
			builder << "<li><a href='/device/edit/${it.getId()}'>${it.getLabel()}</a></li>"
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

private com.hubitat.app.ChildDeviceWrapper CreateChildDriver(String Name, String Vin)
{
	log("CreateChildDriver called", "trace")
	String vehicleNetId = "Hyundai_" + Vin
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
				log.error("-- ${device.label} -- Invalid Log Setting")
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

