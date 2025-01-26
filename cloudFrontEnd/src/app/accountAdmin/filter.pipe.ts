import {Pipe, PipeTransform} from '@angular/core';
import {Account} from "../shared/utils.service";

@Pipe({
    name: 'filter',
    standalone: false
})
export class FilterPipe implements PipeTransform {
  transform(value: Account[], ...args: string[]): Account[] {
    const filterText: string = args[0].toLowerCase();
    const nvrOfflineOnly: boolean = args[1] === 'true';
    const noAccountOnly: boolean = args[2] === 'true';

    if (args[0] === "")
      return value.filter(account => {
        return this.nvrOfflineOnly(account, nvrOfflineOnly) && this.noAccountOnly(account, noAccountOnly);
      });
    else
      return value.filter(account => {
        return (account.productId?.toLowerCase().includes(filterText)
            || account.userName?.toLowerCase().includes(filterText))
          && (this.nvrOfflineOnly(account, nvrOfflineOnly) && this.noAccountOnly(account, noAccountOnly));
      });
  }

  private nvrOfflineOnly(account: Account, nvrOfflineOnly: boolean) {
    return (!account.nvrConnected && nvrOfflineOnly) || !nvrOfflineOnly;
  }

  private noAccountOnly(account: Account, noAccountOnly: boolean) {
    return (!account.accountCreated && noAccountOnly) || !noAccountOnly;
  }
}
