import { Component, OnInit } from '@angular/core';
import {UtilsService, Account} from "../shared/utils.service";

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

  constructor(private utilsService: UtilsService) {
    this.initializeWebSocketConnection();
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

    this.sendMessage("Message");
  }
}
