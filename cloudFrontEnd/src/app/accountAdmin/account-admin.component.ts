import { Component, OnInit } from '@angular/core';
import {UtilsService, Account} from "../shared/utils.service";
import {webSocket} from "rxjs/webSocket";
import { Subscription } from 'rxjs';
import { RxStompService } from '../rxStomp/rx-stomp-service.service';

declare let SockJS: any;
declare let Stomp: any;
@Component({
  selector: 'app-nvradmin',
  templateUrl: './account-admin.component.html',
  styleUrls: ['./account-admin.component.scss']
})
export class AccountAdminComponent implements OnInit {
  downloading: boolean = false;
  accounts: Account[] = [];
  displayedColumns: string[] = ['productId', 'userName', 'nvrConnected', 'usersConnected'];


  private serverUrl = 'http://localhost:4200/stomp'
  private title = 'WebSockets chat';
  private stompClient:any;

  private subs!: Subscription;

  constructor(private utilsService: UtilsService, private rxStompService: RxStompService) {
//    this.initializeWebSocketConnection();
  }

  initializeWebSocketConnection(){
    let ws = new SockJS(this.serverUrl);
    this.stompClient = Stomp.over(ws);
    let that = this;
    this.stompClient.connect({}, function(frame:any) {
      that.stompClient.subscribe("/topic/accountUpdates", (message:any) => {
        if(message.body) {
        //  $(".chat").append("<div class='message'>"+message.body+"</div>")
          console.log(message.body);
        }
      });
    });
  }

  sendMessage(message:any){
    this.stompClient.send("/topic/accountUpdates" , {}, message);
   // $('#input').val('');
  }

  ngOnInit(): void {
    this.utilsService.getAccounts().subscribe((result) => {
      this.accounts = result;
    },
      reason => {

      });

    this.rxStompService.watch("/topic/accountUpdates").subscribe( msg => {
      console.log(msg.body);
    });

// Note that at least one consumer has to subscribe to the created subject - otherwise "nexted" values will be just buffered and not sent,
// since no connection was established!

 //   subject.next({message: 'some message'});
// This will send a message to the server once a connection is made. Remember value is serialized with JSON.stringify by default!

 //   subject.complete(); // Closes the connection.

 //   subject.error({code: 4000, reason: 'I think our app just broke!'});

  }
}
