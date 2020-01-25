package za.uct.cilt.util;
/**********************************************************************************
*
* Copyright (c) 2020 University of Cape Town
*
* Licensed under the Educational Community License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*       http://www.opensource.org/licenses/ECL-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
**********************************************************************************/
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class VulaUtil {
	
	/**
	 * ISO: 2020-01-09T12:02:00.572+02:00
	 * @return
	 */
	public static String getISODate() {
		DateTimeFormatter  dfn = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
		return ZonedDateTime.now().format(dfn);
	}

}
