// parser.go
// go-mqtt-bridge/parser/parser.go
package parser
import (
	"encoding/binary"
	"log"
	"hash/crc32"
)

// --- Data Structures ---
type Vector3D struct {
	X float64 `json:"x"`
	Y float64 `json:"y"`
	Z float64 `json:"z"`
}

type Sample struct {
	SampleIndex       uint32   `json:"sample_index"`
	Acc               Vector3D `json:"acc"`
	Gyro              Vector3D `json:"gyro"`
	Mag               Vector3D `json:"mag"`
	TempRaw           int16    `json:"temp_raw"`
	HeaderTimestampUs uint64   `json:"header_timestamp_us"`
}

type ParsedPacket struct {
	SensorID           string   `json:"sensor_id"`
	HeaderTimestampUs  uint64   `json:"header_timestamp_us"`
	BaseSampleNumber   uint32   `json:"base_sample_number"`
	NumSamplesInPacket uint16   `json:"num_samples_in_packet"`
	Samples            []Sample `json:"samples"`
}

// --- Binary Format Constants ---
const (
	// Header: <BBH => byte, byte, uint16 little-endian
	headerFormatLength = 4 // 1 + 1 + 2
	// CRC: <H => uint16 little-endian
	crcFormatLength = 2
	// Header Values
	headerIDCommand        = 0x7C
	dataTypeImuRawComboV3  = 0x1E
	// Payload Header Fields (All LittleEndian in Python '<I Q H')
	payloadHeaderBaseSize = 4 + 8 + 2 // Size of BaseSampleNum(I) + Timestamp(Q) + NumSamples(H)
	// Sample Data Fields - Mixed Endian!
	sampleV3AccFormatSize  = 6 // '>hhh' = 3 * int16 big-endian
	sampleV3GyroFormatSize = 6 // '>hhh' = 3 * int16 big-endian
	sampleV3MagFormatSize  = 6 // '<hhh' = 3 * int16 little-endian
	sampleV3TempFormatSize = 2 // '>h'   = 1 * int16 big-endian
	// Total size of one sample's data within the payload
	sampleV3TotalSize = sampleV3AccFormatSize + sampleV3GyroFormatSize + sampleV3MagFormatSize + sampleV3TempFormatSize // 20 bytes
)


// --- Conversion Factors ---
const (
	accFactor  = 0.488 / 1000
	gyroFactor = 140.0 / 1000
	magFactor  = 1.0 / 1711.0
)

// GetPartition calculates a deterministic partition for a given sensor ID.
func GetPartition(sensorID string, numPartitions int) int32 {
	checksum := crc32.ChecksumIEEE([]byte(sensorID))
	return int32(checksum % uint32(numPartitions))
}


func ParseFromBuffer(bufferData []byte, sensorID string) ([]ParsedPacket, int) {
	results := []ParsedPacket{}
	consumedBytes := 0
	i := 0 // Current position in bufferData
	totalLen := len(bufferData)
	minLen := headerFormatLength + crcFormatLength // Minimum possible packet length

	for i <= totalLen-minLen {
		offset := i // Start index of the potential packet we are examining

		// Check Header ID (Byte 0)
		if bufferData[offset] != headerIDCommand {
			i++ // Move to the next byte
			continue
		}

		// Check Data Type (Byte 1) - must be after checking bounds
		if offset+1 >= totalLen || bufferData[offset+1] != dataTypeImuRawComboV3 {
			i++ // Move to the next byte
			continue
		}

		// Read Header Payload Length (Bytes 2-3, '<H' -> LittleEndian)
		if offset+headerFormatLength > totalLen {
			break // Not enough data for a full header, wait for more
		}
		payloadLength := binary.LittleEndian.Uint16(bufferData[offset+2 : offset+4])
		expectedTotalLength := headerFormatLength + int(payloadLength) + crcFormatLength

		// Check if the complete packet is potentially in the buffer
		if offset+expectedTotalLength > totalLen {
			// Not enough data for the full packet yet based on header length
			break // Exit the loop, wait for more data
		}

		// Extract the potential packet candidate
		candidatePacket := bufferData[offset : offset+expectedTotalLength]

		// --- Verify CRC --- WARNING: NEEDS REAL IMPLEMENTATION in crc16.go
		validCRC, _, _ := verifyCRC(candidatePacket)
		if !validCRC {
			// Logged inside verifyCRC if needed
			log.Printf("DEBUG: [%s] Invalid CRC at index %d. Skipping.", sensorID, offset)
			i++ // Move one byte forward and try parsing again (might skip valid data if sync is lost)
			continue
		}
		// --- End CRC Check ---

		// --- CRC Seems Valid - Parse Payload ---
		payloadBytes := candidatePacket[headerFormatLength : headerFormatLength+int(payloadLength)]
		if len(payloadBytes) < payloadHeaderBaseSize {
			log.Printf("ERROR: [%s] Payload smaller than base header size (%d bytes) at index %d. Got %d bytes. Skipping packet.",
				sensorID, payloadHeaderBaseSize, offset, len(payloadBytes))
			i += expectedTotalLength // Skip the corrupted packet entirely
			continue
		}

		// Extract payload header fields (All LittleEndian '<' based on Python header structs 'I', 'Q', 'H')
		payloadOffset := 0
		baseSampleNumber := binary.LittleEndian.Uint32(payloadBytes[payloadOffset : payloadOffset+4]) // '<I'
		payloadOffset += 4
		timestampUs := binary.LittleEndian.Uint64(payloadBytes[payloadOffset : payloadOffset+8]) // '<Q'
		payloadOffset += 8
		numSamples := binary.LittleEndian.Uint16(payloadBytes[payloadOffset : payloadOffset+2]) // '<H'
		payloadOffset += 2

		// Check if the declared number of samples seems reasonable and if enough data exists
		if numSamples == 0 {
			log.Printf("WARNING: [%s] Packet at index %d declared 0 samples. Processing header info only.", sensorID, offset)
			// Still create a packet result, but with an empty Samples array
			results = append(results, ParsedPacket{
				SensorID:           sensorID,
				HeaderTimestampUs:  timestampUs,
				BaseSampleNumber:   baseSampleNumber,
				NumSamplesInPacket: numSamples,
				Samples:            []Sample{}, // Empty slice
			})
			i += expectedTotalLength // Move past this packet
			continue                 // Go to next iteration
		}

		expectedDataLen := int(numSamples) * sampleV3TotalSize
		actualDataLen := len(payloadBytes) - payloadOffset // Remaining bytes are sample data
		if actualDataLen < expectedDataLen {
			log.Printf("ERROR: [%s] Not enough data for %d declared samples at index %d. Expected %d bytes, Got %d bytes. Skipping packet.",
				sensorID, numSamples, offset, expectedDataLen, actualDataLen)
			i += expectedTotalLength // Skip the corrupted packet
			continue
		}
		if actualDataLen > expectedDataLen {
			log.Printf("WARNING: [%s] More data (%d bytes) than expected for %d samples (%d bytes) at index %d. Parsing declared samples.",
				sensorID, actualDataLen, numSamples, expectedDataLen, offset)
			// We will only parse numSamples based on expectedDataLen
		}

		// --- Parse individual samples ---
		samples := make([]Sample, 0, numSamples) // Pre-allocate slice capacity
		startIndex := payloadOffset               // Start index of the first sample's data

		packetParsingSuccessful := true // Flag to track if all samples parse correctly
		for j := 0; j < int(numSamples); j++ {
			// Ensure we don't read past the available payload data for this sample
			if startIndex+sampleV3TotalSize > len(payloadBytes) {
				log.Printf("ERROR: [%s] Unexpected end of payload data while parsing sample %d/%d for packet at index %d. Stopping sample parsing for this packet.",
					sensorID, j+1, numSamples, offset)
				packetParsingSuccessful = false
				break // Exit sample loop for this packet
			}

			var sample Sample
			sample.SampleIndex = baseSampleNumber + uint32(j)
			sample.HeaderTimestampUs = timestampUs + uint64(j)*5000 // 5,000 µs per sample @ 200Hz


			// Acc ('>hhh' -> BigEndian)
			accX := int16(binary.BigEndian.Uint16(payloadBytes[startIndex : startIndex+2]))
			accY := int16(binary.BigEndian.Uint16(payloadBytes[startIndex+2 : startIndex+4]))
			accZ := int16(binary.BigEndian.Uint16(payloadBytes[startIndex+4 : startIndex+6]))
			sample.Acc = Vector3D{X: float64(accX) * accFactor, Y: float64(accY) * accFactor, Z: float64(accZ) * accFactor}
			startIndex += sampleV3AccFormatSize

			// Gyro ('>hhh' -> BigEndian)
			gyroX := int16(binary.BigEndian.Uint16(payloadBytes[startIndex : startIndex+2]))
			gyroY := int16(binary.BigEndian.Uint16(payloadBytes[startIndex+2 : startIndex+4]))
			gyroZ := int16(binary.BigEndian.Uint16(payloadBytes[startIndex+4 : startIndex+6]))
			sample.Gyro = Vector3D{X: float64(gyroX) * gyroFactor, Y: float64(gyroY) * gyroFactor, Z: float64(gyroZ) * gyroFactor}
			startIndex += sampleV3GyroFormatSize

			// Mag ('<hhh' -> LittleEndian)
			magX := int16(binary.LittleEndian.Uint16(payloadBytes[startIndex : startIndex+2]))
			magY := int16(binary.LittleEndian.Uint16(payloadBytes[startIndex+2 : startIndex+4]))
			magZ := int16(binary.LittleEndian.Uint16(payloadBytes[startIndex+4 : startIndex+6]))
			sample.Mag = Vector3D{X: float64(magX) * magFactor, Y: float64(magY) * magFactor, Z: float64(magZ) * magFactor}
			startIndex += sampleV3MagFormatSize

			// Temp ('>h' -> BigEndian)
			sample.TempRaw = int16(binary.BigEndian.Uint16(payloadBytes[startIndex : startIndex+2]))
			startIndex += sampleV3TempFormatSize

			samples = append(samples, sample)
		} // End of sample loop

		// Add the parsed packet to results ONLY if all declared samples were parsed
		if packetParsingSuccessful && len(samples) == int(numSamples) {
			results = append(results, ParsedPacket{
				SensorID:           sensorID,
				HeaderTimestampUs:  timestampUs,
				BaseSampleNumber:   baseSampleNumber,
				NumSamplesInPacket: numSamples,
				Samples:            samples,
			})
		} else if packetParsingSuccessful && len(samples) != int(numSamples) {
			// This case should ideally not happen if bounds checks are correct, but added defensively
			log.Printf("WARNING: [%s] Mismatch after sample parsing loop for packet at index %d. Expected %d, got %d. Discarding packet.",
				sensorID, offset, numSamples, len(samples))
		} else {
			// Error already logged inside the loop if !packetParsingSuccessful
			log.Printf("WARNING: [%s] Discarding partially parsed packet from index %d.", sensorID, offset)
		}

		// Move index past the processed (or skipped) packet
		i += expectedTotalLength

	} // End of main parsing loop

	consumedBytes = i // Bytes consumed is the final position 'i'
	return results, consumedBytes
}