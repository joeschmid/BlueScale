
/*
*  
* This file is part of BlueScale.
*
* BlueScale is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
* 
* BlueScale is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Affero General Public License for more details.
* 
* You should have received a copy of the GNU Affero General Public License
* along with BlueScale.  If not, see <http://www.gnu.org/licenses/>.
* 
* Copyright Vincent Marquez 2010
* 
* 
* Please contact us at www.BlueScaleSoftware.com
*
*/
package com.bss.telco.jainsip.unittest

import com.bss.telco.api._
import com.bss.telco.jainsip._
import com.bss.telco._

import org.junit._
import Assert._
import java.util.concurrent.CountDownLatch


class EarlyMediaJoinTwoFunctionalTest extends TestHelper {
	
	var latch:CountDownLatch = null
  
    def getTelcoServer() = telcoServer 

 	def handleDisconnect(conn:SipConnection) = {
 	    //this should be bob!
 	    assertEquals(conn.connectionState, UNCONNECTED())
		System.err.println("assert that alice is disconnected = " + alice.connectionState)
		System.err.println("assert that bob is disconnected = " + bob.connectionState)
		//
		latch.countDown()
 	}

     	 
    getTelcoServer.setDisconnectedCallback( handleDisconnect )
 	val alice = getTelcoServer().createConnection("4445556666", "9495557777")
 	val bob = getTelcoServer().createConnection("1112223333", "7147773333")

    @Test
    def testEarlyMedia() {
        runJoinTwoConnected()
        latch.await()
    }

    @Test
    def testJoinConnect() {
        runJoinConnect()
        latch.await()
    }

    def runJoinConnect() {
        b2bServer.ringSome = true
        latch = new CountDownLatch(1)
 		alice.connect(()=>{ 
		  	assertEquals(alice.connectionState, CONNECTED())
		    alice.join(bob, ()=> {
		        println(" what is the state of bob = " + bob.connectionState + " alice = " + alice.connectionState )

		        assertEquals(alice.connectionState, CONNECTED())
		        assertEquals(bob.connectionState, CONNECTED())
		        println(" alice.joinedTo = " + alice.joinedTo + " | bob.joinedTo = " + bob.joinedTo )
		        //println("are two connected = " + getTelcoServer().areTwoConnected(alice.asInstanceOf[SipConnection], bob.asInstanceOf[SipConnection]) )
                assertTrue(getTelcoServer().areTwoConnected(alice.asInstanceOf[SipConnection], bob.asInstanceOf[SipConnection]))
                bob.disconnect( ()=> {
                    println("disconnected")
                    assertEquals(bob.connectionState, UNCONNECTED() )
                    latch.countDown()
                })
                })
		  } )
    }

	def runJoinTwoConnected() {
 		b2bServer.ringSome = true

 		latch = new CountDownLatch(1)
	          
 		alice.connect(()=>{
		  	assertEquals(alice.connectionState, CONNECTED())
		  	bob.connect(()=>{
		  		assertEquals(bob.connectionState, CONNECTED())
				alice.join(bob, ()=>{
				assertTrue(getTelcoServer.areTwoConnected(alice.asInstanceOf[SipConnection], bob.asInstanceOf[SipConnection]))
				  System.err.println("are both connected = ? " + getTelcoServer().areTwoConnected(alice.asInstanceOf[SipConnection], bob.asInstanceOf[SipConnection]))
				  
				  	alice.disconnect( ()=>{
				  	  println("alice connectionstate = "+ alice.connectionState)
				  		assertEquals(alice.connectionState, UNCONNECTED())
				  		//make sure bob is on hold now!
				  		//assertFalse(getTelcoServer.areTwoConnected(alice.asInstanceOf[SipConnection], bob.asInstanceOf[SipConnection]))
				  		val b = SdpHelper.isBlankSdp(bob.asInstanceOf[JainSipConnection].sdp)
				  		System.err.println("b = " + b)
				  		//Now bob should be disconnected
				    })
				})
			})
		})
	}
 }

object EarlyMediaJoinTwoFunctionalTest {
    def main(args:Array[String]) = {
        val m = new EarlyMediaJoinTwoFunctionalTest()
        m.setUp()
        m.testJoinConnect()
    }
}

