package com.example.football.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.football.entity.Promotion;
import com.example.football.entity.PromotionVersion;
import com.example.football.enums.PromotionDisplayStatus;
import com.example.football.form.PromotionForm;
import com.example.football.service.PromotionService;
import com.example.football.service.UserService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/owner/promotions")
@RequiredArgsConstructor
public class OwnerPromotionController {
    private final PromotionService promotionService;
    private final UserService userService;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) PromotionDisplayStatus status,
                       Authentication authentication, HttpSession session, Model model) {
        Long ownerId = userService.getByUsername(authentication.getName()).getId();
        model.addAttribute("promotions", promotionService.searchOwnerPromotions(ownerId, keyword, status));
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("statuses", PromotionDisplayStatus.values());
        model.addAttribute("reauthenticated", promotionService.isReauthenticated(session));
        return "owner/promotions";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        model.addAttribute("promotionForm", PromotionForm.builder()
                .applicableDays(List.of("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"))
                .build());
        model.addAttribute("editMode", false);
        model.addAttribute("reauthenticated", promotionService.isReauthenticated(session));
        return "owner/promotion-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Authentication authentication, HttpSession session, Model model) {
        Long ownerId = userService.getByUsername(authentication.getName()).getId();
        Promotion p = promotionService.getOwnerPromotion(ownerId, id);
        PromotionForm form = PromotionForm.builder()
                .id(p.getId()).code(p.getCode()).name(p.getName()).description(p.getDescription())
                .discountType(p.getDiscountType()).discountValue(p.getDiscountValue())
                .startAt(p.getStartAt()).endAt(p.getEndAt())
                .applicableDays(p.getApplicableDays() == null ? List.of() : List.of(p.getApplicableDays().split(",")))
                .discountStartTime(p.getDiscountStartTime()).discountEndTime(p.getDiscountEndTime())
                .maxTotalUses(p.getMaxTotalUses()).maxUsesPerCustomer(p.getMaxUsesPerCustomer())
                .build();
        model.addAttribute("promotionForm", form);
        model.addAttribute("editMode", true);
        model.addAttribute("reauthenticated", promotionService.isReauthenticated(session));
        return "owner/promotion-form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("promotionForm") PromotionForm form,
                       Authentication authentication, HttpSession session,
                       RedirectAttributes redirectAttributes) {
        try {
            if (form.getCurrentPassword() != null && !form.getCurrentPassword().isBlank()) {
                boolean ok = promotionService.reauthenticate(authentication.getName(), form.getCurrentPassword(), session);
                if (!ok) throw new IllegalArgumentException("Mật khẩu quản trị hiện tại không chính xác");
            }
            Promotion p = form.getId() == null
                    ? promotionService.createPromotion(form, authentication.getName(), session)
                    : promotionService.updatePromotion(form, authentication.getName(), session);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã lưu khuyến mại " + p.getCode());
            return "redirect:/owner/promotions";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("promotionForm", form);
            return form.getId() == null ? "redirect:/owner/promotions/new"
                    : "redirect:/owner/promotions/" + form.getId() + "/edit";
        }
    }

    @PostMapping("/{id}/stop")
    public String stop(@PathVariable Long id,
                       @RequestParam(value = "currentPassword", required = false) String currentPassword,
                       Authentication authentication, HttpSession session,
                       RedirectAttributes redirectAttributes) {
        try {
            if (currentPassword != null && !currentPassword.isBlank()) {
                boolean ok = promotionService.reauthenticate(authentication.getName(), currentPassword, session);
                if (!ok) throw new IllegalArgumentException("Mật khẩu quản trị hiện tại không chính xác");
            }
            promotionService.stopPromotion(id, authentication.getName(), session);
            redirectAttributes.addFlashAttribute("successMessage", "Đã ngừng áp dụng khuyến mại");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/promotions";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam(value = "currentPassword", required = false) String currentPassword,
                         Authentication authentication, HttpSession session,
                         RedirectAttributes redirectAttributes) {
        try {
            if (currentPassword != null && !currentPassword.isBlank()) {
                boolean ok = promotionService.reauthenticate(authentication.getName(), currentPassword, session);
                if (!ok) throw new IllegalArgumentException("Mật khẩu quản trị hiện tại không chính xác");
            }
            promotionService.deletePromotion(id, authentication.getName(), session);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa khuyến mại");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/promotions";
    }

    @PostMapping("/reauth")
    @ResponseBody
    public java.util.Map<String, Object> reauth(@RequestParam String password,
                                                Authentication authentication, HttpSession session) {
        boolean ok = promotionService.reauthenticate(authentication.getName(), password, session);
        return java.util.Map.of("success", ok,
                "message", ok ? "Xác thực thành công" : "Mật khẩu không chính xác");
    }

    @GetMapping("/{id}/versions")
    public String versions(@PathVariable Long id, Authentication authentication, Model model) {
        Long ownerId = userService.getByUsername(authentication.getName()).getId();
        List<PromotionVersion> versions = promotionService.getVersions(ownerId, id);
        model.addAttribute("versions", versions);
        model.addAttribute("promotion", promotionService.getOwnerPromotion(ownerId, id));
        return "owner/promotion-versions";
    }
}
