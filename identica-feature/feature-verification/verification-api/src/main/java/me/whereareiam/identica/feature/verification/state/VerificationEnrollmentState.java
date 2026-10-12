package me.whereareiam.identica.feature.verification.state;

/**
 * Method-owned state of a pending enrollment, which becomes the stored enrollment once confirmed.
 */
public interface VerificationEnrollmentState extends VerificationProcessState {
	/**
	 * Returns the credential the enrollment stores, such as an authenticator app's secret.
	 *
	 * @return the credential, empty when the method stores none
	 */
	default String credential() {
		return "";
	}

	/**
	 * Returns the label the enrollment is shown with.
	 *
	 * @return the label, empty when the method gives none
	 */
	default String label() {
		return "";
	}

	/**
	 * Returns the counter the enrollment starts with as its
	 * {@linkplain me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment#getAcceptedCounter()
	 * accepted counter}: the counter of the code that confirmed it, so that code cannot be used again.
	 *
	 * @return the counter of the confirming code, {@code 0} for a method without counters
	 */
	default long acceptedCounter() {
		return 0L;
	}
}
