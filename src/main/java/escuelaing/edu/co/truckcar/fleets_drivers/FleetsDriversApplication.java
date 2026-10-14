package escuelaing.edu.co.truckcar.fleets_drivers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FleetsDriversApplication {

	public static void main(String[] args) {
		SpringApplication.run(FleetsDriversApplication.class, args);
	}

}
