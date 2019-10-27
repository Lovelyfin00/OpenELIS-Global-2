package org.openelisglobal.reportconfiguration.service;

import org.openelisglobal.common.service.BaseObjectServiceImpl;
import org.openelisglobal.menu.service.MenuService;
import org.openelisglobal.menu.valueholder.Menu;
import org.openelisglobal.reportconfiguration.dao.ReportDAO;
import org.openelisglobal.reportconfiguration.form.ReportConfigurationForm;
import org.openelisglobal.reportconfiguration.valueholder.Report;
import org.openelisglobal.reportconfiguration.valueholder.ReportCategory;
import org.openelisglobal.siteinformation.service.SiteInformationService;
import org.openelisglobal.siteinformation.valueholder.SiteInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl extends BaseObjectServiceImpl<Report, String> implements ReportService {

    @Autowired
    protected ReportDAO baseObjectDAO;
    @Autowired
    private MenuService menuService;
    @Autowired
    private SiteInformationService siteInformationService;

    ReportServiceImpl() {
        super(Report.class);
    }

    @Override
    public boolean updateReport(ReportConfigurationForm form, String currentUserId) {
        try {
            Report report = baseObjectDAO.get(form.getCurrentReport().getId()).get();
            List<Integer> idOrder = Arrays.asList(form.getIdOrder().split(",")).stream().map(Integer::parseInt).collect(Collectors.toList());

            final String categoryId = form.getCurrentReport().getCategory();
            ReportCategory reportCategory = form.getReportCategoryList().stream().filter( rc -> rc.getId().equalsIgnoreCase(categoryId)).findFirst().orElse(null);
            if (report != null) {
                String previousCategory = report.getCategory();
                report.setName(form.getCurrentReport().getName());
                report.setCategory(form.getCurrentReport().getCategory());
                report.setIsVisible(form.getCurrentReport().getIsVisible());
                report.setSortOrder(idOrder.indexOf(Integer.valueOf(form.getCurrentReport().getId())));
                baseObjectDAO.update(report);
                // If the report category has changed, update the menus.
                if (categoryId != previousCategory) {
                    // new category
                    Menu menu = menuService.getMenuByElementId(reportCategory.getMenuElementId());
                    Menu reportMenu = menuService.getMenuByElementId(report.getMenuElementId());
                    if (menu != null) {
                        reportMenu.setParent(menu);
                        menuService.save(reportMenu);
                    }
                }
            }
            for (int i = 0; i < idOrder.size(); i++) {
                Report r = baseObjectDAO.get(idOrder.get(i) + "").get();
                if (r != null) {
                    Menu menu = menuService.getMenuByElementId(r.getMenuElementId());
                    menu.setIsActive(r.getIsVisible() ? true : false);
                    menu.setPresentationOrder((i + 1) * 50);
                    menuService.save(menu);
                    r.setSortOrder(i);
                    baseObjectDAO.update(r);
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean createNewReport(ReportConfigurationForm form, String currentUserId) {
        try {
            int maxSortOrder = baseObjectDAO.getMaxSortOrder(form.getCurrentReport().getCategory());
            form.getCurrentReport().setSortOrder(maxSortOrder + 1);

            final String categoryId = form.getCurrentReport().getCategory();
            ReportCategory reportCategory = form.getReportCategoryList().stream().filter(rc -> rc.getId().equalsIgnoreCase(categoryId)).findFirst().orElse(null);

            Report createdReport = baseObjectDAO.update(form.getCurrentReport());

            Menu menu = menuService.getMenuByElementId(reportCategory.getMenuElementId());
            /*
             * Create the menu for the new report
             */
            Menu reportMenu = new Menu();
            reportMenu.setElementId(createdReport.getMenuElementId());
            reportMenu.setParent(menu);
            reportMenu.setPresentationOrder((maxSortOrder + 1) * 50);
            reportMenu.setIsActive(createdReport.getIsVisible());
            reportMenu.setDisplayKey(form.getMenuDisplayKey());
            reportMenu.setActionURL(form.getMenuActionUrl());

            reportMenu.setParent(menu);
            menuService.save(reportMenu);

            SiteInformation reportsPath = siteInformationService.getSiteInformationByName("reportsDirectory");
            if (reportsPath != null) {
                String reportPath = reportsPath.getValue();

                if (form.getReportDataFile() != null && form.getReportTemplateFile() != null) {
                    try {
                        File dataFile = new File(reportPath, form.getReportDataFile().getOriginalFilename());
                        form.getReportDataFile().transferTo(dataFile);

                        File templateFile = new File(reportPath, form.getReportTemplateFile().getOriginalFilename());
                        form.getReportTemplateFile().transferTo(templateFile);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    protected ReportDAO getBaseObjectDAO() {
        return baseObjectDAO;
    }
}
