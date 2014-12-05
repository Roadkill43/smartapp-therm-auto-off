/**
 *  honeywell wifi Auto Off
 *
 */

// Automatically generated. Make future change here.
definition(
    name: "Honeywell wifi Auto Off",
    namespace: "roadkill",
    author: "Roadkill43,
    description: "Automatically turn off Honeywell thermostat when windows / doors are opened for a configurable period of time. Turn it back on when all the contacts have closed for a configurable period. ",
    category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png",
    oauth: true
)

preferences {
	section("Control") {
		input("thermostat", "capability.thermostat", title: "Thermostat")
	}
    
    section("Open/Close") {
    	input("sensors", "capability.contactSensor", title: "Sensors", multiple: true)
        input("delay", "number", title: "Delay (minutes) before turning thermostat off", required : false)
        input("delayRestore", "number", title: "Delay (minutes) before restoring when closed", required : false)
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"
        initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
        unschedule()
	initialize()
}

def initialize() {
	state.changed = false
        subscribe(sensors, 'contact', "sensorChange")
}

def sensorChange(evt) {
   log.debug "Desc: $evt.value , $state"
   if(evt.value == 'open' && !state.changed) {
          log.debug "Scheduling turn off ($delay minutes)"
          if(state.scheduledRestore) {   // unscheduled a restore if there is one 
             state.scheduledRestore = false
             unschedule("restore")
          }
          if (delay < 1) {
             turnOff()  
          } else {
             state.scheduled = true
             runIn(delay * 60, turnOff)
          }     
    } else if(evt.value == 'closed' && (state.changed || state.scheduled)) {        
        if(!isOpen()) {
          log.info "Everything is closed, restoring thermostat ($delayRestore minutes)"
          log.debug "state: $state"
          if(state.scheduled)  {
              state.scheduled = false
              unschedule("turnOff")
           } else if (delayRestore < 1) {
              restore()
           } else {
              state.scheduledRestore = true
	      runIn(delayRestore * 60, restore)
           }
         } else {
           log.debug "Something is still open."
           // one or more things are still open.  
         }
    }
}

def isOpen() {
	def result = sensors.find() { it.currentValue('contact') == 'open'; }
       // log.debug "isOpen results: $result"
    
        return result
}

// turn off thermostat based on a contact being opened
def turnOff() {
	log.debug "Preparing to turn off thermostat due to contact open"
    if(isOpen()) {
    	log.debug "It's safe. Turning thermostat off."
        thermostat.poll().    //update mode to make sure we have the latest 
	state.thermostatMode = thermostat.currentValue("thermostatMode")
        state.changed = true
    	thermostat.off()
        state.scheduled = false
    	log.debug "State: $state"
    } else {
        // this should not happen. A turn off should not be ran/scheduled when all contacts are closed
    	log.debug "Shouldn't happen. Scheduled off but everything is closed. The platform did something bad."
    }
}

// restore to thermostatMode prior to cintact being opened 
def restore() {
 if(!isOpen()) {
    log.debug "Setting thermostat to $state.thermostatMode"
    thermostat.setThermostatMode(state.thermostatMode)
    state.changed = false
    state.thermostatMode = ""
    state.scheduledRestore = false
  } else {
    // this should not happen. A restore should not occur when there is an open contact. 
        log.debug "A door must have been reopened"
  }
}
