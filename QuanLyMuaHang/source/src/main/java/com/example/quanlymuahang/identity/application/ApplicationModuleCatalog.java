package com.example.quanlymuahang.identity.application;

import com.example.quanlymuahang.identity.infrastructure.security.AccountPrincipal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Registered applications. Visibility derives from effective backend permissions. */
public final class ApplicationModuleCatalog {
    private ApplicationModuleCatalog() {}
    public record ModuleView(String code, String name, String description, String entryPath) {}
    private record Registration(ModuleView view, Set<String> requiredPermissions, boolean requireAll) {}
    private static final List<Registration> MODULES = List.of(
            new Registration(new ModuleView("PURCHASING", "Mua hàng", "Vật tư, nhà cung cấp, giá và đơn mua hàng.", "/dashboard"), Set.of("PO_READ", "CATALOG_READ"), true),
            new Registration(new ModuleView("ADMINISTRATION", "Quản trị", "Tài khoản, danh bạ, phòng ban và chức vụ.", "/admin/users"), Set.of("USER_READ", "PERSONNEL_READ"), false)
    );
    public static List<ModuleView> accessibleTo(AccountPrincipal principal) {
        Set<String> permissions = principal.getAuthorities().stream().map(authority -> authority.getAuthority()).collect(Collectors.toSet());
        return forPermissions(permissions);
    }

    /** Uses the same permission rule for a logged-in user and an assignable role. */
    public static List<ModuleView> forPermissions(Set<String> permissions) {
        return MODULES.stream().filter(module -> permissions.contains("*") || (module.requireAll()
                        ? permissions.containsAll(module.requiredPermissions())
                        : module.requiredPermissions().stream().anyMatch(permissions::contains)))
                .map(module -> {
                    ModuleView view = module.view();
                    if (view.code().equals("ADMINISTRATION") && !permissions.contains("*") && !permissions.contains("USER_READ"))
                        return new ModuleView(view.code(), view.name(), view.description(), "/admin/employees");
                    return view;
                }).toList();
    }
}
