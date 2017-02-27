/**
 *  Nest Humidity Control
 *
 *  Author: dianoga7@3dgo.net
 *  Editor: bmmiller@gmail.com
 *  Editor: jeff@thechristoffersens.com
 */

preferences {
	input("username", "text", title: "Username", description: "Your Nest username (usually an email address)")
	input("password", "password", title: "Password", description: "Your Nest password")
	input("serial", "text", title: "Serial #", description: "The serial number of your thermostat")
}

// for the UI
metadata {
	definition (name: "Nest Humidity Control", namespace: "jdc0730", author: "dianoga7@3dgo.net") {
		capability "Actuator"
		capability "Health Check"
		capability "Polling"
		capability "Relative Humidity Measurement"
		capability "Sensor"
		capability "Switch"
		capability "Switch Level"

// attribute switch  off, on
// commands off, on
// attribute level 0-100
// setLevel(number, rate)  0-100


		command "setHumiditySetpoint"
		command "humiditySetpointUp"
		command "humiditySetpointDown"

		attribute "humiditySetpoint", "number"
		attribute "lastPoll", "STRING"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"humidity", type:"generic", width: 6, height: 4) {
			tileAttribute("device.humidity", key: "PRIMARY_CONTROL") {
				attributeState "default", label:'${currentValue}%', backgroundColor:"#007ea7"
			}

			tileAttribute("humiditySetpoint", key: "SECONDARY_CONTROL") {
				attributeState "default", label: 'Setpoint: ${currentValue}%'
			}
		}

		standardTile("refresh", "device.thermostatMode", decoration: "flat", width: 2, height: 2) {
			state "default", action:"polling.poll", icon:"st.secondary.refresh"
		}

		standardTile("humiditySetpointUp", "device.humiditySetpoint", decoration: "flat", width: 2, height: 2) {
			state "humiditySetpointUp", label:'  ', action:"humiditySetpointUp", icon:"st.thermostat.thermostat-up"
		}

		standardTile("humiditySetpointDown", "device.humiditySetpoint", decoration: "flat", width: 2, height: 2) {
			state "humiditySetpointDown", label:'  ', action:"humiditySetpointDown", icon:"st.thermostat.thermostat-down"
		}

		controlTile("humSliderControl", "device.humiditySetpoint", "slider", height: 2, width: 4, inactiveLabel: false, range: "0..80") {
                        state "setHumSetpoint", action:"setHumiditySetpoint", backgroundColor: "#1e9cbb"
                }

		main(["humidity"])

		details(["humidity", "humSliderControl", "refresh"])

	}
}

def initialize() {
	log.trace "initialized..."
	unschedule()

	def random = new Random()
	def random_int = random.nextInt(60)
	def random_dint = random.nextInt(7)
	schedule("${random_int} ${random_dint}/7 * * * ?", poll)
	log.info "POLL scheduled (${random_int} ${random_dint}/7 * * * ?)"

	def val = device.currentValue("checkInterval")
	def timeOut = 1800
	if(!val || val.toInteger() != timeOut) {
		log.debug "Updating Device Health Check Interval to $timeOut"
		sendEvent(name: "checkInterval", value: timeOut, data: [protocol: "cloud"], displayed: false)
	}
}

def ping() {
	log.debug "ping..."
	poll()
}

void installed() {
	log.trace "installed..."
	initialize()
}

void updated() {
	log.trace "updated..."
	initialize()
}


// parse events into attributes
def parse(String description) {

}

// handle commands
def isOn() {
	int setPoint = device.latestValue("humiditySetpoint")
	int curHum = device.latestValue("humidity")
	return curHum < setPoint
}

def on() {
	log.debug "on()..."
	int setPoint = device.latestValue("humiditySetpoint")
	int curHum = device.latestValue("humidity")
	if(curHum < setPoint) { return }
	else {
		def newSP = curHum + 3
		setHumiditySetpoint(newSP)
	}
}

def off() {
	log.debug "off()..."
	int setPoint = device.latestValue("humiditySetpoint")
	int curHum = device.latestValue("humidity")
	if(curHum < setPoint) {
		def newSP = curHum - 3
		setHumiditySetpoint(newSP)
	}
}

def setLevel(number) {
	setHumiditySetpoint(number)
}

def setLevel(number, rate) {
	setHumiditySetpoint(number)
}

def humiditySetpointUp() {
	int newSetpoint = device.latestValue("humiditySetpoint") + 1
	setHumiditySetpoint(newSetpoint)
}

def humiditySetpointDown() {
	int newSetpoint = device.latestValue('humiditySetpoint') - 1
	setHumiditySetpoint(newSetpoint)
}

def setHumiditySetpoint(humiditySP) {
	log.debug "setHumiditySetpoint ${humiditySP}%"

	def newHum = humiditySP.toInteger()
	if (humiditySP < 0 || humiditySP > 80) { newHum = 45 }
	if (newHum != device.latestValue('humiditySetpoint')) {
		api('humidity', ['target_humidity': newHum]) {
			sendEvent(name: 'humiditySetpoint', value: newHum, unit: '%')
		}
		log.debug "Setting humidity set to: ${newHum}%"
	}
	poll()
}


def poll() {
	log.debug "Executing 'poll'"
	api('status', []) {
		data.device = it.data.device.getAt(settings.serial)
/*
		data.shared = it.data.shared.getAt(settings.serial)
		data.structureId = it.data.link.getAt(settings.serial).structure.tokenize('.')[1]
		data.structure = it.data.structure.getAt(data.structureId)
		log.debug("data.shared: " + data.shared)
*/

		def humidity = data.device.current_humidity
		def humiditySetpoint = Math.round(data.device.target_humidity)

		if(humidity && humidity <= 100) {
			sendEvent(name: 'humidity', value: humidity, unit: '%')
		}
		if(humiditySetpoint && humiditySetpoint <= 100) {
			sendEvent(name: 'humiditySetpoint', value: humiditySetpoint, unit: '%')
			sendEvent(name: 'level', value: humiditySetpoint, unit: '%')
			if(isOn()) {
				sendEvent(name: 'switch', value:"on")
			} else {
				sendEvent(name: 'switch', value:"off")
			}
		}

		def now=new Date()
		def tz = location.timeZone
		def nowString = now.format("MMM/dd HH:mm",tz)
		sendEvent("name":"lastPoll", "value":nowString, displayed: false)
	}
}

def api(method, args = [], success = {}) {
	if(!isLoggedIn()) {
		log.debug "Need to login"
		login(method, args, success)
		return
	}

	def methods = [
		'status': [uri: "/v2/mobile/${data.auth.user}", type: 'get'],
		'humidity': [uri: "/v2/put/device.${settings.serial}", type: 'post'],
	]

	def request = methods.getAt(method)

	log.debug "Logged in"
	doRequest(request.uri, args, request.type, success)
}

// Need to be logged in before this is called. So don't call this. Call api.
def doRequest(uri, args, type, success) {
	log.debug "Calling $type : $uri : $args"

	if(uri.charAt(0) == '/') {
		uri = "${data.auth.urls.transport_url}${uri}"
	}

	def params = [
		uri: uri,
		headers: [
			'X-nl-protocol-version': 1,
			'X-nl-user-id': data.auth.userid,
			'Authorization': "Basic ${data.auth.access_token}"
		],
		body: args
	]

	def postRequest = { response ->
		if (response.getStatus() == 302) {
			def locations = response.getHeaders("Location")
			def location = locations[0].getValue()
			log.debug "redirecting to ${location}"
			doRequest(location, args, type, success)
		} else {
			success.call(response)
		}
	}

	try {
		if (type == 'post') {
			httpPostJson(params, postRequest)
		} else if (type == 'get') {
			httpGet(params, postRequest)
		}
	} catch (Throwable e) {
		login()
	}
}

def login(method = null, args = [], success = {}) {
	def params = [
		uri: 'https://home.nest.com/user/login',
		body: [username: settings.username, password: settings.password]
	]

	httpPost(params) {response ->
		data.auth = response.data
		data.auth.expires_in = Date.parse('EEE, dd-MMM-yyyy HH:mm:ss z', response.data.expires_in).getTime()
		log.debug data.auth

		api(method, args, success)
	}
}

def isLoggedIn() {
	if(!data.auth) {
		log.debug "No data.auth"
		return false
	}

	def now = new Date().getTime();
	return data.auth.expires_in > now
}
