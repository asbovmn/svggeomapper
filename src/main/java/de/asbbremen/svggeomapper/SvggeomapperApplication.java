package de.asbbremen.svggeomapper;

import de.asbbremen.svggeomapper.view.MainFrame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.*;
import java.awt.*;

@SpringBootApplication
public class SvggeomapperApplication {
	public static void main(String[] args) {
		ConfigurableApplicationContext ctx = new SpringApplicationBuilder(SvggeomapperApplication.class).headless(false).run(args);

		SwingUtilities.invokeLater(() -> {
			MainFrame mainFrame = ctx.getBean(MainFrame.class);
			mainFrame.setVisible(true);
		});
	}

}

