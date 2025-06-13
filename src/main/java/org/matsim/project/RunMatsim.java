/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package org.matsim.project;

import extensions.selectors.LLMPlanSelector;
import extensions.selectors.LLMPlanStrategyProvider;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.replanning.PlanStrategyImpl;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.scenario.ScenarioUtils;

/**
 * @author nagel
 *
 */
public class RunMatsim{

	public static void main(String[] args) {

		Config config;
		if ( args==null || args.length==0 || args[0]==null ){
			config = ConfigUtils.loadConfig( "scenarios/equil/config.xml" );
		} else {
			config = ConfigUtils.loadConfig( args );
		}

		config.controller().setOverwriteFileSetting( OverwriteFileSetting.deleteDirectoryIfExists );

		// possibly modify config here

		// ---
		
		Scenario scenario = ScenarioUtils.loadScenario(config) ;

		// possibly modify scenario here
		
		// ---
		
		Controler controler = new Controler( scenario ) ;
		
		// possibly modify controler here

//		controler.addOverridingModule( new OTFVisLiveModule() ) ;

//		controler.addOverridingModule( new SimWrapperModule() );
		
		// ---
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addPlanStrategyBinding("LLMPlanStrategy").toInstance(
						new PlanStrategyImpl(new LLMPlanSelector<>(getApiKey()))
				);
			}
		});
		
		controler.run();
	}

	// Add this helper method to your main class
	private static String getApiKey() {
		String key = System.getenv("GROK_API_KEY");
		if (key != null && !key.trim().isEmpty()) {
			return key;
		}

		key = System.getProperty("grok.api.key");
		if (key != null && !key.trim().isEmpty()) {
			return key;
		}

		throw new RuntimeException("Grok API key not found. Please set GROK_API_KEY environment variable or grok.api.key system property.");
	}
	
}
