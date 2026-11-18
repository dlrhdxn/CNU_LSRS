package com.cnu.lsrs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class QrCodeImageGenerator {

	private static final int IMAGE_SIZE = 320;

	private final ObjectMapper objectMapper;

	public QrCodeImageGenerator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public byte[] generate(String seatCode, String qrToken) {
		try {
			String payload = objectMapper.writeValueAsString(new QrPayload(seatCode, qrToken));
			var matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, IMAGE_SIZE, IMAGE_SIZE,
					Map.of(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
							EncodeHintType.MARGIN, 2));
			try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
				MatrixToImageWriter.writeToStream(matrix, "PNG", output);
				return output.toByteArray();
			}
		} catch (WriterException | IOException e) {
			throw new IllegalStateException("체크인 QR 이미지를 생성하지 못했습니다.", e);
		}
	}

	public record QrPayload(String seatCode, String qrToken) {
	}
}
