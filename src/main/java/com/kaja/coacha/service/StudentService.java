package com.kaja.coacha.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.kaja.coacha.model.Student;



@Service
public class StudentService {
	
	public List<Student> getAllStudents() {
		
		Student student1 = new com.kaja.coacha.model.Student("Alice", 20);
		Student student2 = new com.kaja.coacha.model.Student("Bob", 22);
		Student student3 = new com.kaja.coacha.model.Student("Charlie", 19);

		List<Student> students = new ArrayList<>();
		students.add(student1);
		students.add(student2);
		students.add(student3);
		return students;
	}	
} 
