import { Injectable } from '@angular/core';
import {ActivatedRouteSnapshot, CanActivate, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable } from 'rxjs';
import { UtilsService } from '../shared/utils.service';
import {map} from "rxjs/operators";

@Injectable({
  providedIn: 'root'
})
export class OnlyLoggedInService implements CanActivate{

  constructor(private utilsService: UtilsService) { }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    return this.utilsService.getUserAuthorities().pipe(
      // Map to true if authority is ROLE_CLIENT or ROLE_ADMIN (i.e. logged in)
      map((val) =>
        val.find(v => v.authority === 'ROLE_CLIENT' || v.authority === 'ROLE_ADMIN') !== undefined
      ));
  }
}
