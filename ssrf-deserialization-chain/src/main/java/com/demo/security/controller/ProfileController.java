package com.demo.security.controller;

import java.net.HttpURLConnection;
import java.net.URL;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    // @PostMapping("/avatar/fetch")
    // public String fetchAvatar(@RequestParam("imageUrl") String imageUrl) {
    //     try {
    //         URL url = new URL(imageUrl);
    //         HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    //         int responseCode = connection.getResponseCode();
    //         return "Fetched image URL: " + imageUrl + " responded with status " + responseCode;
    //     } catch (Exception ex) {
    //         return "Error: " + ex.getMessage();
    //         // Adding a comment to indicate that this is a potential SSRF vulnerability. In a real-world application, you should validate the URL and restrict access to internal resources.
    //     }
    // }
}
