
package com.example.football.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/owner")
public class OwnerPaymentController {

    @GetMapping("/payments")
    public String showPaymentManage() {
        return "owner/payment-manage";
    }
}
