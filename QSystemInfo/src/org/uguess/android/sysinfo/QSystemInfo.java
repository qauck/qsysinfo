/********************************************************************************
 * (C) Copyright 2000-2009, by Shawn Qualia.
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

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.widget.TabHost;

/**
 * QSystemInfo
 */
public final class QSystemInfo extends TabActivity
{

	/** Called when the activity is first created. */
	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		requestWindowFeature( Window.FEATURE_NO_TITLE );

		TabHost th = getTabHost( );

		Intent it = new Intent( Intent.ACTION_VIEW );
		it.setClass( this, SysInfoManager.class );
		th.addTab( th.newTabSpec( SysInfoManager.class.getName( ) )
				.setContent( it )
				.setIndicator( getResources( ).getString( R.string.tab_info ),
						getResources( ).getDrawable( R.drawable.info ) ) );

		it = new Intent( Intent.ACTION_VIEW );
		it.setClass( this, ApplicationManager.class );
		th.addTab( th.newTabSpec( ApplicationManager.class.getName( ) )
				.setContent( it )
				.setIndicator( getResources( ).getString( R.string.tab_apps ),
						getResources( ).getDrawable( R.drawable.applications ) ) );

		it = new Intent( Intent.ACTION_VIEW );
		it.setClass( this, ProcessManager.class );
		th.addTab( th.newTabSpec( ProcessManager.class.getName( ) )
				.setContent( it )
				.setIndicator( getResources( ).getString( R.string.tab_procs ),
						getResources( ).getDrawable( R.drawable.processes ) ) );
	}

}