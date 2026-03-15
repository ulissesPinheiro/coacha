package com.kaja.coacha.controller;

import java.util.List;
import java.util.Map;

import com.kaja.coacha.model.Student;
import com.kaja.coacha.service.StudentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudentController {
	private StudentService studentService;

	@Value("${APP_ENV:local}")
	private String appEnv;

	@Value("${APP_GREETING:Olá local!}")
	private String appGreeting;

	@Value("${MAX_STUDENTS:10}")
	private String maxStudents;

	public StudentController(StudentService studentService) {
		this.studentService = studentService;
	}

	@GetMapping("/students")
	public List<Student> getAllStudents() {
		return studentService.getAllStudents();
	}

	@Value("${API_KEY:nao-definida}")
	private String apiKey;

	@Value("${DB_PASSWORD:nao-definida}")
	private String dbPassword;

	@GetMapping("/info")
	public Map<String, String> getInfo() {
		return Map.of(
			"env",         appEnv,
			"greeting",    appGreeting,
			"maxStudents", maxStudents
		);
	}

	@GetMapping("/secret-check")
	public Map<String, String> getSecretCheck() {
		// Nunca exponha o valor real — só confirma se foi injetado
		return Map.of(
			"api_key_present",     apiKey.equals("nao-definida")    ? "NAO" : "SIM",
			"db_password_present", dbPassword.equals("nao-definida") ? "NAO" : "SIM",
			"api_key_preview",     apiKey.substring(0, 5) + "****"
		);
	}
}
