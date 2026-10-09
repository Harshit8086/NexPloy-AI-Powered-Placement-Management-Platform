package com.nexploy.campuslink.document.controller;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.document.dto.DocumentResponse;
import com.nexploy.campuslink.document.entity.Document;
import com.nexploy.campuslink.document.repository.DocumentFileRepository;
import com.nexploy.campuslink.document.repository.DocumentRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A student's document checklist, campus-scoped. Backs the student Documents screen. */
@RestController
@RequestMapping("/api/students/{studentId}/documents")
@RequiredArgsConstructor
public class StudentDocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final StudentRepository studentRepository;
    private final CampusAccessService campusAccessService;

    @GetMapping
    public List<DocumentResponse> listForStudent(@PathVariable String studentId) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        campusAccessService.requireAccess(user, student.getCampusId());
        List<Document> documents = documentRepository.findByStudentId(studentId);
        // One id-only query for the batched hasFile flag — no blob load, no N+1.
        Set<String> idsWithFile = documents.isEmpty()
            ? Set.of()
            : Set.copyOf(documentFileRepository.findIdsByIdIn(documents.stream().map(Document::getId).toList()));
        return documents.stream()
            .map(document -> DocumentResponse.from(document, idsWithFile.contains(document.getId())))
            .toList();
    }
}
