import { TestBed } from '@angular/core/testing';

import { WifiUtilsService } from './wifi-utils.service';

describe('WifiUtilsService', () => {
  let service: WifiUtilsService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(WifiUtilsService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
