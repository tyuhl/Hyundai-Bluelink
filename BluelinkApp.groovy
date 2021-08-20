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
 *
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

static String appVersion()   { return "1.0.0" }
def setVersion(){
	state.name = "Hyundai Bluelink Application"
	state.version = "1.0.0"
}

@Field static String global_apiURL = "https://api.telematics.hyundaiusa.com"
@Field static String client_id = "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920"
@Field static String client_secret = "v558o935-6nne-423i-baa8"

definition(
		name: "Hyundai Bluelink App",
		namespace: "tyuhl",
		author: "Tim Yuhl",
		description: "Application for Hyundai Bluelink web service access.",
		importUrl:"",
		category: "Convenience",
		iconUrl: "",
		iconX2Url: ""
)

preferences {
	page(name: "mainPage")
	page(name: "accountInfoPage")
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
		section(getFormat("header-blue-grad","   2.  Use This Button To Discover Vehicles and Create Drivers for Each")) {
			input 'discover', 'button', title: 'Discover Registered Vehicles', submitOnChange: true
		}
		section(getFormat("header-blue-grad","Change Logging Level")) {
			input name: "logging", type: "enum", title: "Log Level", description: "Debug logging", required: false, submitOnChange: true, defaultValue: "INFO", options: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
		}
		getDebugLink()
	}
}

def accountInfoPage()
{
	dynamicPage(name: "accountInfoPage", title: "Set Bluelink Account Information", install: false, uninstall: false) {
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

////////
// Debug Stuff
///////
def getDebugLink() {
	section{
		href(
				name       : 'debugHref',
				title      : 'Debug buttons',
				page       : 'debugPage',
				description: 'Access debug buttons (authorize, refresh token, etc.)'
		)
	}
}

def debugPage() {
	dynamicPage(name:"debugPage", title: "Debug", install: false, uninstall: false) {
		section {
			paragraph "Debug buttons"
		}
		section {
			input 'refreshToken', 'button', title: 'Force Token Refresh', submitOnChange: true
		}
		section {
			input 'initialize', 'button', title: 'initialize', submitOnChange: true
		}
		section {
			input 'getVehicles', 'button', title: 'Get Vehicles', submitOnChange: true
		}
	}
}

def appButtonHandler(btn) {
	switch (btn) {
		case 'discover':
			authorize()
			getVehicles()
			break;
		case 'refreshToken':
			refreshToken()
			break
		case 'initialize':
			initialize()
			break
		case 'getVehicles':
			getVehicles()
			break
		default:
			log("Invalid Button In Handler", "error")
	}
}

void installed() {
	log("Installed with settings: ${settings}", "trace")
	initialize()
}

void updated() {
	log("Updatedwith settings: ${settings}", "trace")
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
	setVersion()
	unschedule()
}

void authorize() {
	log("authorize called", "trace")

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
		httpPost(params) { response -> authResponse(response) }
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		log("Login failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
	}
}

void refreshToken() {
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
			httpPost(params) { response -> authResponse(response) }
		}
		catch (groovyx.net.http.HttpResponseException e)
		{
			log("Login failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
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

		Integer expireTime = (Integer.parseInt(reJson.expires_in) - 100)
		log("Bluelink token refreshed successfully, Next Scheduled in: ${expireTime} sec", "debug")
		runIn(expireTime, refreshToken)
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
	def reJson = ''
	try
	{
		httpGet(params) { response ->
			def reCode = response.getStatus();
			reJson = response.getData();
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
			getVehicles(device, true)
		}
		log("getVehicles failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
		return;
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
					sendEvent(newDevice, [name: "NickName", value:  vehicle.vehicleDetails.nickName])
					sendEvent(newDevice, [name: "VIN", value:  vehicle.vehicleDetails.vin])
					sendEvent(newDevice, [name: "RegId", value:  vehicle.vehicleDetails.regid])
					sendEvent(newDevice, [name: "Odemeter", value:  vehicle.vehicleDetails.odemeter])
					sendEvent(newDevice, [name: "Model", value:  vehicle.vehicleDetails.series])
					sendEvent(newDevice, [name: "Trim", value:  vehicle.vehicleDetails.trim])
					sendEvent(newDevice, [name: "vehicleGeneration", value:  vehicle.vehicleDetails.vehicleGeneration])
					sendEvent(newDevice, [name: "brandIndicator", value:  vehicle.vehicleDetails.brandIndicator])
				 }
			}
	}
}

void getVehicleStatus(com.hubitat.app.DeviceWrapper device, Boolean refresh = false, Boolean retry=false)
{
	log("getVehicleStatus() called", "trace")

	//Note: this API can take up to a minute tor return if REFRESH=true because it contacts the car's modem and
	//doesn't use cached info.
	def uri = global_apiURL + "/ac/v2/rcs/rvs/vehicleStatus"
	def headers = getDefaultHeaders(device)
	headers.put('offset', '-5')
	headers.put('REFRESH', refresh.toString())
	String valTimeout = refresh ? '240' : '10' //timeout in sec.
	def params = [ uri: uri, headers: headers, timeout: valTimeout ]
	log("getVehicleStatus ${params}", "debug")

	//add error checking
	def reJson = ''
	try
	{
		httpGet(params) { response ->
			def reCode = response.getStatus();
			reJson = response.getData();
			log("reCode: ${reCode}", "debug")
			log("reJson: ${reJson}", "debug")
		}
		// Update relevant device attributes
		sendEvent(device, [name: 'Engine', value: reJson.vehicleStatus.engine ? 'On' : 'Off'])
		sendEvent(device, [name: 'DoorLocks', value: reJson.vehicleStatus.doorLock ? 'Locked' : 'Unlocked'])
		sendEvent(device, [name: 'Trunk', value: reJson.vehicleStatus.trunkOpen ? 'Open' : 'Closed'])
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

void Lock(com.hubitat.app.DeviceWrapper device)
{
	if( !LockUnlockHelper(device, '/ac/v2/rcs/rdo/off') )
	{
		log("Lock call failed -- try waiting before retrying", "info")
	} else
	{
		log("Lock call made to car -- can take some time to lock", "info")
	}
}

void Unlock(com.hubitat.app.DeviceWrapper device)
{
	if( !LockUnlockHelper(device, '/ac/v2/rcs/rdo/on') )
	{
		log("Unlock call failed -- try waiting before retrying", "info")
	}else
	{
		log("Unlock call made to car -- can take some time to unock", "info")
	}
}

void Start(com.hubitat.app.DeviceWrapper device, Boolean retry=false)
{
	log("Start() called", "trace")

	def uri = global_apiURL + '/ac/v2/rcs/rsc/start'
	def headers = getDefaultHeaders(device)
	headers.put('offset', '-4')
	String theVIN = device.currentValue("VIN")
	def body = [
			"userName": user_name,
			"vin": theVIN,
			"ims": "0",
			"airCtrl" : "0",
			"airTemp" : ["unit" : 1, "value": "70"],
			"defrost" : "false",
			"heating1" : "0",
			"ignitionDuration" : "10",
			"seatHeaterVentInfo" : ""  //unknown
	]

	def params = [ uri: uri, headers: headers, body: body, timeout: '10' ]
	log("Start ${params}", "debug")

	int reCode = 0
	try
	{
		httpPost(params) { response ->
			reCode = response.getStatus();
			if (reCode == 200) {
				log("Vehicle successfully started.","info")
			}
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		if (e.getStatusCode() == 401 && !retry)
		{
			log('Authorization token expired, will refresh and retry.', 'warn')
			refreshToken()
			Start(device, true)
		}
		log("Start vehicle failed -- ${e.getLocalizedMessage()}: Status: ${e.response.getStatus()}", "error")
	}
}

void Stop(com.hubitat.app.DeviceWrapper device, Boolean retry=false)
{
	log("Stop() called", "trace")

	def uri = global_apiURL + '/ac/v2/rcs/rsc/stop'
	def headers = getDefaultHeaders(device)
	headers.put('offset', '-4')
	def params = [ uri: uri, headers: headers, timeout: '10' ]
	log("Stop ${params}", "debug")

	int reCode = 0
	try
	{
		httpPost(params) { response ->
			reCode = response.getStatus();
			if (reCode == 200) {
				log("Vehicle successfully stopped.","info")
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
		}
		log("Stop vehicle failed -- ${e.getLocalizedMessage()}: Status: ${e.response.getStatus()}", "error")
	}
}

///
// Supporting helpers
///
private Boolean LockUnlockHelper(com.hubitat.app.DeviceWrapper device, String urlSuffix, Boolean retry=false)
{
	log("LockUnlockHelper() called", "trace")

	def uri = global_apiURL + urlSuffix
	def headers = getDefaultHeaders(device)
	headers.put('offset', '-5')
	String theVIN = device.currentValue("VIN")
	def body = [
			"userName": user_name,
			"vin": theVIN
	]

	def params = [ uri: uri, headers: headers, body: body, timeout: '10' ]
	log("LockUnlockHelper ${params}", "debug")

	int reCode = 0
	try
	{
		httpPost(params) { response ->
			reCode = response.getStatus();
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

private LinkedHashMap<String, String> getDefaultHeaders(com.hubitat.app.DeviceWrapper device) {
	log("getDefaultHeaders() called", "trace")

	LinkedHashMap<String, String> theHeaders = [];
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
	if(type == "header-blue-grad") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background: linear-gradient(to bottom, #d4e4ef 0%,#86aecc 100%);  border: 2px'>${myText}</div>"
	if(type == "header-center-blue-grad") return "<div style='text-align:center; color:#000000; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background: linear-gradient(to bottom, #d4e4ef 0%,#86aecc 100%);  border: 2px'>${myText}</div>"
	if(type == "item-light-grey") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; font-weight: normal; padding-left: 10px; background-color:#D8D8D8; border: 1px solid'>${myText}</div>"
	if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}

