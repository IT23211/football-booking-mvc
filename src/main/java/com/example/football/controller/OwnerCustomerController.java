
package com.example.football.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;

@Controller
@RequestMapping("/owner/customers")
@PreAuthorize("hasRole('OWNER')")
public class OwnerCustomerController {

    // TN-13: Hiển thị giao diện quản lý tài khoản khách hàng
    @GetMapping
    public String customerManagement() {
        return "owner/customer-manage";
    }
}

