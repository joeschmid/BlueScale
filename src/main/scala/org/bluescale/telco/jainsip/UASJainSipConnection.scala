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
* Please contact us at www.BlueScale.org
*
*/
package org.bluescale.telco.jainsip

import org.bluescale.telco.jainsip._
import org.bluescale.telco._
import org.bluescale.telco.Types._
import org.bluescale.telco.api._
import org.bluescale.util.LogHelper
import scala.collection.immutable.Map
import javax.sip.header._
import javax.sip._
import javax.sdp.SessionDescription
import javax.sip.message._
import org.bluescale.util.BlueFuture._
import org.bluescale.telco.Types
import org.bluescale.util._
import org.bluescale._

trait UASJainSipConnection extends BaseJainSipConnection with LogHelper {
    
    def bye(tx:ServerTransaction) = orderedexec {
        _state = UNCONNECTED()
        serverTx = Some(tx)

        telco.removeConnection(this)
        onDisconnect()
        disconnectCallback.foreach(_(this))
    }

    def reinvite(tx:ServerTransaction, sdp:SessionDescription) = orderedexec {
        this.serverTx = Some(tx)
        this.sdp = sdp ///here is the weird part? 
        serverTx = Some(tx)
        joinedTo.foreach(join => join.joinedMediaChange())
        val joinable = joinedTo.getOrElse(telco.silentJoinable())
        for(_ <- incomingResponse(200, joinable)){} 
    }

    def invite(tx:ServerTransaction, sdp:SessionDescription) = orderedexec {
        this.sdp = sdp
        serverTx = Some(tx)
    }
    
    def cancel(cancelTx:ServerTransaction) = orderedexec {
        //TODO: isn't this already cancelled? 
        serverTx.foreach( tx => 
            telco.internal.sendResponse(487, tx, null)
        )
    	
        telco.internal.sendResponse(200, cancelTx, null) 
        _state match {
        	case UNCONNECTED() =>
        		telco.removeConnection(this)
        		incomingCancelCallback.foreach(_(this))
        	case _ => println("this state = " + _state)
        }
 	}
 	
 	def ack(newtx:ServerTransaction) = orderedexec {
        serverTx.foreach( tx => { 
            _state = CONNECTED()
            callbacks.get(tx.getBranchId()).foreach( callback => callback match {
                case f:( ()=>Unit ) =>
                    clearCallbacks(tx)
                    f()
                case _ => println("error")
            })
        })
    }
 	  
    private def incomingResponse(responseCode:Int, toJoin:Joinable[_]) = wrapPromise[SipConnection](promise => orderedexec {
        serverTx.foreach( tx => {
            callbacks += tx.getBranchId()->(() => { 
            	promise.success(this)
            })
		    telco.internal.sendResponse(responseCode, tx, toJoin.sdp.toString().getBytes())  
	 	})
    })
    
    def accept[T <: Joinable[T]](toJoin: T) = wrapPromise[SipConnection]( promise => 
    	for(conn <- incomingResponse(200,toJoin);
    		toJoin <- toJoin.connect[SipConnection](this)) {
    		val t = toJoin
    		val s = _joinedTo
    		
    		_joinedTo = Some(toJoin)
    		promise.success(this)
    	}
    ) 
    
    def accept() =
	    accept[SdpJoinable](SdpHelper.getBlankJoinable(telco.contactIp))

    def ring(toJoin:Joinable[_]) =
        incomingResponse(183, toJoin)
	
    def ring() =
        incomingResponse(180, telco.silentJoinable()) 
 
	def reject() = 
	    incomingResponse(606, telco.silentJoinable())

    protected def loadInitialSdp():SessionDescription
}
