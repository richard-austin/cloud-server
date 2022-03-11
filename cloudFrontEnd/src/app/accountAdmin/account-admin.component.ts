import {ViewChild} from '@angular/core';
import {ElementRef} from '@angular/core';
import {Component, OnInit} from '@angular/core';
import {MatCheckboxChange} from '@angular/material/checkbox';
import {UtilsService, Account} from "../shared/utils.service";
import {animate, state, style, transition, trigger} from "@angular/animations";
import {Camera} from "../cameras/Camera";

declare let SockJS: any;
declare let Stomp: any;

@Component({
  selector: 'app-nvradmin',
  templateUrl: './account-admin.component.html',
  styleUrls: ['./account-admin.component.scss'],
  animations: [
    trigger('detailExpand', [
      state('collapsed', style({height: '0px', minHeight: '0'})),
      state('expanded', style({height: '*'})),
      transition('expanded <=> collapsed', animate('225ms cubic-bezier(0.4, 0.0, 0.2, 1)')),
    ]),
    trigger('openClose', [
      // ...
      state('open', style({
        transform: 'rotate(90deg)'
      })),
      state('closed', style({
        transform: 'rotate(0deg)'
      })),
      transition('open => closed', [
        animate('.2s')
      ]),
      transition('closed => open', [
        animate('.2s')
      ]),
    ])
  ],
})
export class AccountAdminComponent implements OnInit {
  downloading: boolean = false;
  accounts: Account[] = [];
  displayedColumns: string[] = ['changePassword', 'disableAccount', 'productId', 'accountCreated', 'userName', 'nvrConnected', 'usersConnected'];
  changePasswordColumns: string[] = ['password', 'confirmPassword', 'cancel', 'confirm'];
  @ViewChild('filter') filterEl!: ElementRef<HTMLInputElement>
  private stompClient: any;
  filterText: string = "";
  bOnlyNVROffline: boolean = false;
  bNoAccountOnly: boolean = false;
  errorMessage: string = '';
  successMessage: string = '';
  expandedElement: Account | null | undefined = undefined;


  constructor(private utilsService: UtilsService) {
    this.initializeWebSocketConnection();
  }

  initializeWebSocketConnection() {
    let serverUrl: string = window.location.origin + "/stomp";

    let ws = new SockJS(serverUrl);
    this.stompClient = Stomp.over(ws);
    this.stompClient.debug = null;
    let that = this;
    this.stompClient.connect({}, () => {
      that.stompClient.subscribe("/topic/accountUpdates", (message: any) => {
        if (message.body) {
          let msgObj = JSON.parse(message.body);
          if (msgObj.message === "update") {
            that.getAccounts();
            console.log(message.body);
          }
        }
      });
    });
  }

  getAccounts(): void {
    this.utilsService.getAccounts().subscribe((result) => {
        this.accounts = result;
      },
      reason => {
        this.errorMessage = reason.error;
      });
  }

  sendMessage(message: any) {
    this.stompClient.send("/topic/accountUpdates", {}, message);
    // $('#input').val('');
  }

  updateFilter() {
    this.filterText = this.filterEl.nativeElement.value;
  }

  changePassword(account: Account) {
    if(this.expandedElement === undefined)
      this.expandedElement = account;
    else
      this.expandedElement = undefined;
  }

  setAccountEnabledStatus(account: Account, $event: MatCheckboxChange) {
    account.accountEnabled = $event.checked;
    this.utilsService.setAccountEnabledStatus(account).subscribe(() => {
        this.successMessage = "Account " + account.userName + " now " + (account.accountEnabled ? "enabled" : "disabled");
      },
      reason => {
        account.accountEnabled = !$event.checked;  // Roll back local copy if it failed.
        this.errorMessage = reason.error;
      })
  }

  onlyNVROffline($event: MatCheckboxChange) {
    this.bOnlyNVROffline = $event.checked
  }

  noAccountOnly($event: MatCheckboxChange) {
    this.bNoAccountOnly = $event.checked
  }

  ngOnInit(): void {
    this.getAccounts();

// Note that at least one consumer has to subscribe to the created subject - otherwise "nexted" values will be just buffered and not sent,
// since no connection was established!

    //   subject.next({message: 'some message'});
// This will send a message to the server once a connection is made. Remember value is serialized with JSON.stringify by default!

    //   subject.complete(); // Closes the connection.

    //   subject.error({code: 4000, reason: 'I think our app just broke!'});

  }
}