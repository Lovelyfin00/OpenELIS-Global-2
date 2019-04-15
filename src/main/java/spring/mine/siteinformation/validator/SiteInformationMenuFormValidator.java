package spring.mine.siteinformation.validator;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import spring.mine.common.validator.ValidationHelper;
import spring.mine.siteinformation.form.SiteInformationMenuForm;

@Component
public class SiteInformationMenuFormValidator implements Validator {

	@Override
	public boolean supports(Class<?> clazz) {
		return SiteInformationMenuForm.class.equals(clazz);
	}

	@Override
	public void validate(Object target, Errors errors) {
		SiteInformationMenuForm form = (SiteInformationMenuForm) target;

		// menulist does not need validation

		for (String id : form.getSelectedIDs()) {
			ValidationHelper.validateIdField(id, "selectedIDs", errors, true);
		}

		ValidationHelper.validateOptionField(form.getSiteInfoDomainName(), "siteInfoDomainName", errors,
				new String[] { "non_conformityConfiguration", "WorkplanConfiguration", "PrintedReportsConfiguration",
						"sampleEntryConfig", "ResultConfiguration", "MenuStatementConfig", "PaitientConfiguration",
						"SiteInformation" });
	}

}