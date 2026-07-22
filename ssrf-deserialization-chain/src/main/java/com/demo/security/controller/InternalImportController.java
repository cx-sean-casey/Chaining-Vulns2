package com.demo.security.controller;

import java.io.IOException;
import java.io.ObjectInputStream;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalImportController {

    @PostMapping("/import")
    public ResponseEntity<String> importData(HttpServletRequest request) throws IOException, ClassNotFoundException {
        String remoteAddr = request.getRemoteAddr();
        if (!"127.0.0.1".equals(remoteAddr) && !"0:0:0:0:0:0:0:1".equals(remoteAddr)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }

        ObjectInputStream objectInputStream = new ObjectInputStream(request.getInputStream());
        Object payload = objectInputStream.readObject();
        return ResponseEntity.ok("Imported object: " + payload.getClass().getName());
    }
}
