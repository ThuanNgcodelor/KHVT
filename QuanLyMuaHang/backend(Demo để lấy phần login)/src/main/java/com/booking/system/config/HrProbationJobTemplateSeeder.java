package com.booking.system.config;

import com.booking.system.hr.entity.HrDepartment;
import com.booking.system.hr.entity.HrPosition;
import com.booking.system.hr.entity.HrProbationJobTemplate;
import com.booking.system.hr.entity.HrWorkingCondition;
import com.booking.system.hr.enums.HrCatalogStatus;
import com.booking.system.hr.repository.HrDepartmentRepository;
import com.booking.system.hr.repository.HrPositionRepository;
import com.booking.system.hr.repository.HrProbationJobTemplateRepository;
import com.booking.system.hr.repository.HrWorkingConditionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds editable probation job templates extracted from the 2026 probation
 * contract source document. Only reusable business fields are seeded; candidate
 * names, citizen IDs, birth dates and addresses from the source document are
 * intentionally not persisted here.
 */
@Component
@RequiredArgsConstructor
@Order(20)
public class HrProbationJobTemplateSeeder implements CommandLineRunner {

    private static final String SYSTEM_ACTOR = "SYSTEM:HR_PROBATION_TEMPLATE_SEED";
    private static final String SOURCE_NOTE = "Seed từ file Mẫu Hợp đồng thử việc 2026.docx; chỉ giữ dữ liệu mẫu công việc, không giữ dữ liệu cá nhân.";
    private static final String CONTRACT_TYPE = "Xác định thời hạn 02 tháng";
    private static final String GENERIC_RULE_NOTE = "Chấp hành nội quy công ty và sự phân công của người quản lý trực tiếp.";

    private static final List<DefaultTemplate> DEFAULT_TEMPLATES = List.of(
            new DefaultTemplate(
                    "TV-VIDEO",
                    "Sáng tạo nội dung Video",
                    null,
                    "Sáng tạo nội dung Video",
                    null,
                    "Sáng tạo nội dung Video theo chỉ đạo của Ban lãnh đạo Công ty.",
                    7_000_000,
                    "đồng/tháng và KPI",
                    10
            ),
            new DefaultTemplate(
                    "TV-ONLINE-SALES",
                    "Xây dựng kênh bán hàng Online",
                    "Phòng Kinh doanh",
                    "Xây dựng kênh bán hàng Online",
                    null,
                    "Xây dựng kênh bán hàng Online theo chỉ đạo của Ban lãnh đạo Công ty.",
                    7_000_000,
                    "đồng/tháng và KPI",
                    20
            ),
            new DefaultTemplate(
                    "TV-QLCLSP",
                    "Nhân viên phòng QLCLSP",
                    "Phòng QLCLSP",
                    "Nhân viên phòng QLCLSP",
                    null,
                    "Do trưởng phòng phân công.",
                    7_500_000,
                    "đồng/tháng",
                    30
            ),
            new DefaultTemplate(
                    "TV-KY-THUAT-CD",
                    "Nhân viên kỹ thuật cơ điện",
                    "Phòng Kỹ thuật",
                    "Nhân viên kỹ thuật cơ điện",
                    null,
                    "Do trưởng phòng phân công.",
                    7_500_000,
                    "đồng/tháng",
                    40
            ),
            new DefaultTemplate(
                    "TV-KHO",
                    "Nhân viên kho",
                    "Bộ phận kho",
                    "Nhân viên kho",
                    null,
                    "Theo yêu cầu bộ phận kho.",
                    7_500_000,
                    "đồng/tháng",
                    50
            ),
            new DefaultTemplate(
                    "TV-SALE",
                    "Nhân viên sale",
                    "Phòng Kinh doanh",
                    "Nhân viên sale",
                    null,
                    "Do trưởng phòng Kinh doanh phân công.",
                    8_000_000,
                    "đồng/tháng",
                    60
            ),
            new DefaultTemplate(
                    "TV-MARKETING",
                    "Quản lý Marketing và bán hàng Online",
                    "Phòng Kinh doanh",
                    "Quản lý Marketing và bán hàng Online",
                    null,
                    "Quản lý Marketing và bán hàng Online theo chỉ đạo của Ban lãnh đạo Công ty.",
                    12_750_000,
                    "đồng/tháng và KPI",
                    70
            ),
            new DefaultTemplate(
                    "TV-TCHC-CDS",
                    "Chuyển đổi số, hỗ trợ phòng TCHC",
                    "Tổ chức",
                    "Chuyển đổi số, hỗ trợ phòng TCHC",
                    null,
                    "Chuyển đổi số, hỗ trợ phòng TCHC và các công việc theo chỉ đạo của Ban lãnh đạo Công ty.",
                    7_500_000,
                    "đồng/tháng",
                    80
            ),
            new DefaultTemplate(
                    "TV-XNK",
                    "Nhân viên xuất nhập khẩu",
                    "Phòng XNK",
                    "Nhân viên xuất nhập khẩu",
                    null,
                    "Các công việc liên quan đến xuất nhập khẩu theo sự chỉ đạo của Phòng Xuất nhập khẩu và Ban lãnh đạo Công ty.",
                    8_000_000,
                    "đồng/tháng",
                    90
            )
    );

    private final HrProbationJobTemplateRepository templateRepository;
    private final HrDepartmentRepository departmentRepository;
    private final HrPositionRepository positionRepository;
    private final HrWorkingConditionRepository workingConditionRepository;

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;
        for (DefaultTemplate seed : DEFAULT_TEMPLATES) {
            if (templateRepository.findByCode(seed.code()).isPresent()
                    || templateRepository.findByName(seed.name()).isPresent()) {
                continue;
            }
            HrProbationJobTemplate template = new HrProbationJobTemplate();
            template.setCode(seed.code());
            template.setName(seed.name());
            template.setDescription(SOURCE_NOTE);
            template.setDepartment(findDepartment(seed.departmentName()));
            template.setPosition(findPosition(seed.positionName()));
            template.setWorkingCondition(findWorkingCondition(seed.workingConditionName()));
            template.setProbationContractType(CONTRACT_TYPE);
            template.setJobDescription(seed.jobDescription());
            template.setBaseSalary(BigDecimal.valueOf(seed.baseSalary()));
            template.setSalaryNote(seed.salaryNote());
            template.setDepartmentRuleNote(GENERIC_RULE_NOTE);
            template.setStatus(HrCatalogStatus.ACTIVE);
            template.setSortOrder(seed.sortOrder());
            template.setCreatedByActor(SYSTEM_ACTOR);
            template.setUpdatedByActor(SYSTEM_ACTOR);
            templateRepository.save(template);
            created++;
        }
        if (created > 0) {
            System.out.println("Đã tạo " + created + " mẫu công việc thử việc mặc định.");
        }
    }

    private HrDepartment findDepartment(String name) {
        if (name == null || name.isBlank()) return null;
        for (String candidate : departmentAliases(name)) {
            HrDepartment department = departmentRepository.findByName(candidate).orElse(null);
            if (department != null) return department;
        }
        return null;
    }

    private HrPosition findPosition(String name) {
        if (name == null || name.isBlank()) return null;
        for (String candidate : positionAliases(name)) {
            HrPosition position = positionRepository.findByName(candidate).orElse(null);
            if (position != null) return position;
        }
        return null;
    }

    private HrWorkingCondition findWorkingCondition(String name) {
        if (name == null || name.isBlank()) return null;
        return workingConditionRepository.findByName(name).orElse(null);
    }

    private static List<String> departmentAliases(String name) {
        return switch (name) {
            case "Bộ phận kho" -> List.of("Bộ phận kho", "Bộ phận kho (BX)", "Kho vận", "Phòng Kho vận");
            case "Phòng XNK" -> List.of("Phòng XNK", "XNK PBHC", "Xuất nhập khẩu", "Phòng Xuất nhập khẩu");
            case "Tổ chức" -> List.of("Tổ chức", "Phòng TCHC", "TCHC");
            default -> List.of(name);
        };
    }

    private static List<String> positionAliases(String name) {
        return switch (name) {
            case "Nhân viên kỹ thuật cơ điện" -> List.of(
                    "Nhân viên kỹ thuật cơ điện",
                    "Nhân viên phòng Kỹ thuật cơ điện",
                    "Nhân viên phòng Phòng kỷ thuật cơ điện"
            );
            default -> List.of(name);
        };
    }

    private record DefaultTemplate(
            String code,
            String name,
            String departmentName,
            String positionName,
            String workingConditionName,
            String jobDescription,
            long baseSalary,
            String salaryNote,
            int sortOrder
    ) {
    }
}
