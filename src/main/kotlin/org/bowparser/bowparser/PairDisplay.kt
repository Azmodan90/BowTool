package org.bowparser.bowparser

import com.fazecast.jSerialComm.SerialPort

object PairDisplay {
    @JvmStatic
    fun main(args: Array<String>) {
        val comPort = SerialPort.getCommPorts()[0]
        DisplayPairer(comPort, 19200, 1).scan()
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
class DisplayPairer(serialPort: SerialPort, baudRate: Int, private var index: Int) : StdLoop(serialPort, baudRate) {
    private enum class State {
        GET_DISPLAY_SERIAL,
        GET_STORED_SERIAL,
        PUT_SERIAL,
        CHECK_STORED_SERIAL
    }

    private var state = State.GET_DISPLAY_SERIAL
    private var displaySerial: List<UByte> = emptyList()
    private var motorDisplaySerial: List<UByte> = emptyList()

    fun scan() {
        if (!open()) return

        loop(Mode.CHECK_BAT)
    }

    override fun sendCommand() {
        when (state) {
            State.GET_DISPLAY_SERIAL -> sendGetDisplaySerial(displayId.toUByte())
            State.GET_STORED_SERIAL -> sendGetDisplaySerialFromMotor(index)
            State.PUT_SERIAL -> sendStoreDisplaySerialInMotor(index, *displaySerial.toUByteArray())
            State.CHECK_STORED_SERIAL -> sendGetDisplaySerialFromMotor(index)
        }
    }

    override fun handleResponse(message: Message): Result {
        when (state) {
            State.GET_DISPLAY_SERIAL -> {
                if (message.tgt() == pcId && message.isRsp() && message.src() == displayId && message.isCmd(0x20)) {
                    displaySerial = message.data()
                    log("Display serial: ${hex(displaySerial)}")
                    state = State.GET_STORED_SERIAL
                    return Result.SEND_COMMAND
                }
            }

            State.GET_STORED_SERIAL -> {
                if (message.tgt() == pcId && message.isRsp() && message.src() == motorId && message.isCmd(0x08)) {
                    motorDisplaySerial = message.data().drop(4)
                    log("Display serial stored in motor slot ${index + 1}: ${hex(motorDisplaySerial)}")
                    if (displaySerial.equals(motorDisplaySerial)) {
                        log("Serials already match, no change made")
                        return Result.DONE
                    }
                    state = State.PUT_SERIAL
                    return Result.SEND_COMMAND
                }
            }

            State.PUT_SERIAL -> {
                if (message.tgt() == pcId && message.isRsp() && message.src() == motorId && message.isCmd(0x09)) {
                    log("New display serial stored in motor!")
                    state = State.CHECK_STORED_SERIAL
                    return Result.SEND_COMMAND
                }
            }

            State.CHECK_STORED_SERIAL -> {
                if (message.tgt() == pcId && message.isRsp() && message.src() == motorId && message.isCmd(0x08)) {
                    log("Display serial stored in motor slot ${index + 1}: ${hex(message.data().drop(4))}")
                    return Result.DONE
                }
            }
        }
        return Result.CONTINUE
    }

}