package parser
import (
	"encoding/binary"
	"log"
	// Import the chosen CRC library (aliased as 'crc')
	crc "github.com/sigurn/crc16"
)

// Precompute the CRC table for CRC-16/KERMIT for performance.

var crc16ArcParams = crc.Params{
	Poly:   0x8005, // Polynomial (equivalent to 0x18005)
	Init:   0x0000, // Initial Value
	RefIn:  true,   // Reflect Input Bytes? YES
	RefOut: true,   // Reflect Output CRC? YES <-- THIS IS CRITICAL for ARC
	XorOut: 0x0000, // Final XOR value
	Check:  0xBB3D, // Standard check value for "123456789"
	Name:   "CRC-16/ARC",
}

// --- Precompute the table using the specific parameters ---
var crcTableARC = crc.MakeTable(crc.CRC16_ARC) // Use the existing constant

// --- CRC Calculation function using the generic Checksum and the ARC table ---
func calculateCRC16(data []byte) uint16 {
	// Use the generic Checksum function with the precomputed ARC table.
	// This function internally handles Init, Update, and Complete (including RefOut/XorOut).
	return crc.Checksum(data, crcTableARC)
}


// Define a base filename for the dumped packets
const packetDumpBaseFilename = "verifycrc_input_packet"


// checks the CRC of a received packet and writes the input packet to a file.
func verifyCRC(packet []byte) (bool, uint16, uint16) {

	// --- Original CRC Verification Logic ---
	const crcFormatLength = 2 // '<H' = 2 bytes LittleEndian

	if len(packet) < crcFormatLength {
		log.Printf("ERROR: Packet too short for CRC check: len=%d", len(packet))
		// Don't try to calculate CRC if packet is too short
		return false, 0, 0
	}
	
	// Extract data part and expected CRC part
	dataToCRC := packet[:len(packet)-crcFormatLength]
	expectedCRCBytes := packet[len(packet)-crcFormatLength:]


	// '<H' in Python struct means LittleEndian unsigned short (uint16)
	expectedCRC := binary.LittleEndian.Uint16(expectedCRCBytes)

	// Calculate CRC using the proper function
	calculatedCRC := calculateCRC16(dataToCRC)

	// Perform the actual comparison
	isValid := calculatedCRC == expectedCRC

	// Log if there's a mismatch
	if !isValid {
		log.Printf("ERROR: CRC mismatch! Calculated %d (0x%X), Expected %d (0x%X)",
			calculatedCRC, calculatedCRC, expectedCRC, expectedCRC)
		log.Printf("       Data part checked (Hex): %x", dataToCRC) // Log data on mismatch
	}

	return isValid, calculatedCRC, expectedCRC
}
