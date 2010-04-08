/********************************************************************************
 * (C) Copyright 2000-2010, by Shawn Qualia.
 *
 * This library is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Lesser General Public License as published by 
 * the Free Software Foundation; either version 2.1 of the License, or 
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public 
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License 
 * along with this library; if not, write to the Free Software Foundation, 
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 *
 * [Java is a trademark or registered trademark of Sun Microsystems, Inc. 
 * in the United States and other countries.]
 ********************************************************************************/

package org.uguess.android.sysinfo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.widget.Toast;

/**
 * Util
 */
final class Util
{

	static int getIntOption( Activity ac, String key, int defValue )
	{
		return ac.getPreferences( Context.MODE_PRIVATE ).getInt( key, defValue );
	}

	static void setIntOption( Activity ac, String key, int val )
	{
		Editor et = ac.getPreferences( Context.MODE_PRIVATE ).edit( );
		et.putInt( key, val );
		et.commit( );
	}

	static boolean getBooleanOption( Activity ac, String key )
	{
		return ac.getPreferences( Context.MODE_PRIVATE ).getBoolean( key, true );
	}

	static void setBooleanOption( Activity ac, String key, boolean val )
	{
		Editor et = ac.getPreferences( Context.MODE_PRIVATE ).edit( );
		et.putBoolean( key, val );
		et.commit( );
	}

	static void shortToast( Context context, int resId )
	{
		Toast.makeText( context, resId, Toast.LENGTH_SHORT ).show( );
	}

	static void shortToast( Context context, String msg )
	{
		Toast.makeText( context, msg, Toast.LENGTH_SHORT ).show( );
	}

	static boolean updateIntOption( Intent data, Activity ac, String key,
			int defValue )
	{
		int t = data.getIntExtra( key, defValue );
		if ( t != getIntOption( ac, key, defValue ) )
		{
			setIntOption( ac, key, t );
			return true;
		}
		return false;
	}

	static boolean updateBooleanOption( Intent data, Activity ac, String key )
	{
		boolean b = data.getBooleanExtra( key, true );
		if ( b != getBooleanOption( ac, key ) )
		{
			setBooleanOption( ac, key, b );
			return true;
		}
		return false;
	}
}
