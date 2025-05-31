package tools.vitruv.methodologisttemplate.vsum.uncertainty;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import brakesystem.BrakeDisk;
import brakesystem.Brakesystem;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;
import uncertainty.Uncertainty;
import uncertainty.UncertaintyAnnotationRepository;
import uncertainty.UncertaintyLocation;
import uncertainty.UncertaintyLocationType;

public class UncertaintyConsistencyTest {

	private static final Logger logger = org.slf4j.LoggerFactory
			.getLogger(UncertaintyConsistencyTest.class);

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
				new XMIResourceFactoryImpl());

	}

	@Test
	void updateLocationTest(@TempDir Path tempDir) {

		VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
		// Registers a Brakesystem, CADRepository and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		// Add a BrakeDisk that in turn (by reactions) creates a Circle
		UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120);

		// Add two uncertainties to the brake disk
		CommittableView brakeAndUncertaintyView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeDerivingTrait();
		modifyView(brakeAndUncertaintyView, (CommittableView v) -> {
			BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream()
					.filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
					.filter(d -> d.getDiameterInMM() == 120)
					.findFirst().orElseThrow();

			UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
					.createUncertaintyLocation(List.of(brakeDisk));
			uncertaintyLocation.setSpecification("FromDisk");
			uncertaintyLocation.setLocation(UncertaintyLocationType.ANALYSIS);

			Uncertainty uncertainty = UncertaintyTestFactory
					.createUncertainty(Optional.of(uncertaintyLocation));

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
					.getUncertainties().add(uncertainty);

			// Trigger propagation
			brakeDisk.setSpecificationType(EcoreUtil.generateUUID());

		});

		// Assert that two uncertainties now exist both having the same location
		Assertions.assertTrue(
				assertView(
						UncertaintyTestUtil.getDefaultView(vsum,
								List.of(UncertaintyAnnotationRepository.class,
										Brakesystem.class)),
						(View v) -> {
							List<Uncertainty> uncertainties = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next().getUncertainties();
							return uncertainties.size() == 2 && uncertainties.stream()
									.allMatch(u -> u.getUncertaintyLocation()
											.getSpecification()
											.equals("FromDisk")
											&& u.getUncertaintyLocation()
													.getLocation() == UncertaintyLocationType.ANALYSIS);

						}));

		// Change the location of the first uncertainty to DECISION_MAKING
		modifyView(UncertaintyTestUtil
				.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeDerivingTrait(), (CommittableView v) -> {
					List<Uncertainty> uncertainties = v
							.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next().getUncertainties();
					Uncertainty firstUncertainty = uncertainties.get(0);
					UncertaintyLocation uncertaintyLocation = firstUncertainty
							.getUncertaintyLocation();
					uncertaintyLocation.setLocation(UncertaintyLocationType.DECISION_MAKING);
				});

		// Change the specification of the first uncertainty to "specificationTwo"
		modifyView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class))

				.withChangeDerivingTrait(), (CommittableView v) -> {
					List<Uncertainty> uncertainties = v
							.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next().getUncertainties();
					Uncertainty firstUncertainty = uncertainties.get(0);
					UncertaintyLocation uncertaintyLocation = firstUncertainty
							.getUncertaintyLocation();
					uncertaintyLocation.setSpecification("specificationTwo");
				});

		// Assert that both uncertainties now have the location DECISION_MAKING and the
		// specification "specificationTwo"
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum,
						List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							List<Uncertainty> uncertainties = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next().getUncertainties();
							return uncertainties.size() == 2 && uncertainties.stream()
									.allMatch(u -> u.getUncertaintyLocation()
											.getLocation()
											.equals(UncertaintyLocationType.DECISION_MAKING)
											&& u.getUncertaintyLocation()
													.getSpecification()
													.equals("specificationTwo"));

						}));

	}

	// These functions are only for convience, as they make the code a bit better
	// readable
	private void modifyView(CommittableView view, Consumer<CommittableView> modificationFunction) {
		modificationFunction.accept(view);
		view.commitChanges();
	}

	private boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
		return viewAssertionFunction.apply(view);
	}
}
